# Called inside the guarded environment process. Admin/loader secrets never enter Maven's environment.
param([ValidateSet('Base','Variant','Budget','Scale')][string]$Suite='Base',[string]$Case='BASE')
$ErrorActionPreference='Stop'
if ($Case -notmatch '^[A-Z_]+$') { throw 'Invalid verification case label' }
$reportPrefix = $Case
function Snapshot-I2([string]$tag) {
    $tables = (Invoke-I2Sql 'root' $adminPassword "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' ORDER BY TABLE_NAME;") -split '\r?\n'
    $rows = @()
    foreach ($table in $tables) {
        if ($table -notmatch '^[a-z_][a-z0-9_]*$') { throw 'Unexpected table identifier' }
        $columns = (Invoke-I2Sql 'root' $adminPassword "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table' ORDER BY ORDINAL_POSITION;") -split '\r?\n'
        $parts = @($columns | ForEach-Object { if ($_ -notmatch '^[a-z_][a-z0-9_]*$') { throw 'Unexpected column identifier' }; 'IF(`'+$_+'` IS NULL,NULL,HEX(`'+$_+'`))' })
        $data = Invoke-I2Sql 'root' $adminPassword ("SELECT JSON_ARRAY(" + ($parts -join ',') + ") AS row_data FROM $table ORDER BY row_data;") $db
        $ddl = Invoke-I2Sql 'root' $adminPassword "SHOW CREATE TABLE $table;" $db
        $count = Invoke-I2Sql 'root' $adminPassword "SELECT COUNT(*) FROM $table;" $db
        $rows += [ordered]@{table=$table;count=[long]$count;dataSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($data))).ToLowerInvariant();schemaSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($ddl))).ToLowerInvariant()}
    }
    $result=[ordered]@{identity=$id;tag=$tag;checkedAt=(Get-Date).ToString('o');tables=$rows;binaryLogStatus=(Invoke-I2Sql 'root' $adminPassword 'SHOW BINARY LOG STATUS;');loaderLocked=(Invoke-I2Sql 'root' $adminPassword "SELECT account_locked FROM mysql.user WHERE User='kg_i2_loader' AND Host='127.0.0.1';")}
    [IO.File]::WriteAllText((Join-Path $reportRoot "I2-$reportPrefix-$tag.json"),($result|ConvertTo-Json -Depth 8),$utf8)
    return $result
}
$before=Snapshot-I2 'BEFORE'
if ($before.loaderLocked -ne 'Y') { throw 'Loader must be locked for the reader-only verification window' }
[void](Invoke-I2Sql 'root' $adminPassword 'SET GLOBAL general_log=ON;')
$exitCode=-1
try {
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=Join-Path $PSHOME 'pwsh.exe'; $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    $start.StandardOutputEncoding=$utf8; $start.StandardErrorEncoding=$utf8
    foreach($arg in @('-NoProfile','-File',(Join-Path $reportRoot 'Invoke-I2Tests.ps1'),'-Suite',$Suite)) { [void]$start.ArgumentList.Add($arg) }
    foreach($key in @('MYSQL_PWD','KG_I2_ADMIN_PASSWORD','KG_I2_LOADER_PASSWORD')) { [void]$start.Environment.Remove($key) }
    $start.Environment['KG_I2_AUTHORIZED']=$expectedUuid
    $start.Environment['KG_I2_READER_PASSWORD']=$readerPassword
    $start.Environment['KG_I2_CASE']=$Case
    $child=[Diagnostics.Process]::Start($start)
    $outTask=$child.StandardOutput.ReadToEndAsync(); $errTask=$child.StandardError.ReadToEndAsync()
    $deadline=(Get-Date).AddMinutes(5)
    while(-not $child.WaitForExit(1000)) { if((Get-Date) -gt $deadline) { $child.Kill($true); throw 'Bounded I2 test process exceeded five minutes' } }
    $exitCode=$child.ExitCode
    $log=Redact ($outTask.GetAwaiter().GetResult()+"`n"+$errTask.GetAwaiter().GetResult())
    [IO.File]::WriteAllText((Join-Path $reportRoot "I2-$reportPrefix-MAVEN.log"),$log,$utf8)
    $resultClasses = switch ($Suite) { 'Base' {@('I2JdbcBaselineTest','I2ReadOnlyPrivilegeTest')} 'Variant' {@('I2FaultVariantTest')} 'Budget' {@('I2QueryBudgetTest')} 'Scale' {@('I2ScaleQueryTest')} }
    foreach($resultClass in $resultClasses) {
        $xmlPath=Join-Path $projectRoot "backend-java/target/surefire-reports/TEST-com.knowledgegraph.assistant.i2.$resultClass.xml"
        if(Test-Path -LiteralPath $xmlPath) {
            $xml=[xml][IO.File]::ReadAllText($xmlPath)
            # Surefire properties may include process details; archive only test names/results, not raw XML.
            $summary=@{case=$Case;class=$resultClass;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped;cases=@($xml.testsuite.testcase | ForEach-Object { @{name=$_.name;seconds=$_.time;failure=[string]$_.failure.message;error=[string]$_.error.message} });rawXmlSha256=(Get-FileHash -LiteralPath $xmlPath).Hash.ToLowerInvariant()}
            [IO.File]::WriteAllText((Join-Path $reportRoot "I2-$reportPrefix-$resultClass.json"),($summary|ConvertTo-Json -Depth 8),$utf8)
        }
    }
    Write-Output $log
} finally {
    [void](Invoke-I2Sql 'root' $adminPassword 'SET GLOBAL general_log=OFF;')
    $after=Snapshot-I2 'AFTER'
    $same=($before.tables|ConvertTo-Json -Depth 5 -Compress) -ceq ($after.tables|ConvertTo-Json -Depth 5 -Compress)
    $binlogSame=$before.binaryLogStatus -ceq $after.binaryLogStatus
    $activeReaders=[int](Invoke-I2Sql 'root' $adminPassword "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='kg_i2_reader' AND COMMAND<>'Sleep';")
    $proof=@{stage='I2';phase='static_reader_window';case=$Case;mavenExitCode=$exitCode;allTableCount=$before.tables.Count;dataAndSchemaEqual=$same;binlogPositionEqual=$binlogSame;loaderLockedBefore=$before.loaderLocked;loaderLockedAfter=$after.loaderLocked;activeReaderQueriesAfter=$activeReaders;generalLogSha256=(Get-FileHash -LiteralPath (Join-Path $envRoot 'mysql-general.log')).Hash.ToLowerInvariant();testProcessHasOnlyReaderCredential=$true;modelConnected=$false;springApplicationStarted=$false}
    [IO.File]::WriteAllText((Join-Path $reportRoot "I2-$reportPrefix-ZERO-WRITE.json"),($proof|ConvertTo-Json -Depth 8),$utf8)
    if(-not $same -or -not $binlogSame) { throw 'I2 reader window changed business/schema/binlog state: stop, do not reset' }
}
if($exitCode -ne 0) { throw 'I2 real JDBC tests did not all pass; stop for diagnosis without changing frozen contracts' }
