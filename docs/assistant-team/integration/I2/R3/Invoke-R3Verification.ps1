[CmdletBinding()]
param([ValidateSet('Offline','Real')][string]$Phase)
$ErrorActionPreference='Stop'
$projectRoot='D:\知识图谱'
$envRoot='D:\知识图谱-i2-env'
$aliasRoot='D:\kg-i2-env'
$bin=Join-Path $aliasRoot 'mysql-8.4.11-winx64/bin'
$r3=Join-Path $projectRoot 'docs/assistant-team/integration/I2/R3'
$db='kg_assistant_i2_20260927_r01'
$expectedUuid='8c6cfcb3-ba49-11f1-bf64-005056c00001'
$utf8=[Text.UTF8Encoding]::new($false)
$secrets=[Collections.Generic.List[string]]::new()
$guard=& (Join-Path $r3 'Verify-R3Baseline.ps1') -RequirePackage
$runId=$Phase+'-'+(Get-Date).ToString('yyyyMMddTHHmmssfff')
$reportRoot=Join-Path $r3 "evidence/$runId"
if(Test-Path -LiteralPath $reportRoot){throw 'Evidence directory collision; never overwrite'}
[void](New-Item -ItemType Directory -Path $reportRoot)
function Save-New([string]$Name,$Value){
    if($Name -notmatch '^[A-Za-z0-9_.-]+$'){throw 'Invalid evidence filename'}
    $path=Join-Path $reportRoot $Name
    if(Test-Path -LiteralPath $path){throw "Evidence collision: $Name"}
    [IO.File]::WriteAllText($path,($Value|ConvertTo-Json -Depth 60),$utf8)
}
Save-New 'BASELINE-BEFORE.json' $guard
# Load only reviewed helper definitions, never execute the old environment entrypoint.
foreach($spec in @(@('Invoke-I2Environment.ps1',@('Redact','Invoke-I2Sql','Unprotect')),@('Invoke-I2BaseVerification.ps1',@('Snapshot-I2')))){
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path (Split-Path $r3) $spec[0]),[ref]$tokens,[ref]$errors)
    if($errors.Count){throw 'Frozen helper script parse error'}
    foreach($name in $spec[1]){
        $definitions=@($ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)|Where-Object Name -eq $name)
        if($definitions.Count -ne 1){throw "Helper definition mismatch: $name"}
        . ([scriptblock]::Create($definitions[0].Extent.Text))
    }
}
function Run-TestGroup([string]$Suite,[bool]$Real){
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=Join-Path $PSHOME 'pwsh.exe';$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $start.StandardOutputEncoding=$utf8;$start.StandardErrorEncoding=$utf8
    foreach($arg in @('-NoProfile','-File',(Join-Path $r3 'Invoke-R3Tests.ps1'),'-Suite',$Suite,'-ReportSuffix',($runId+'-'+$Suite))){[void]$start.ArgumentList.Add($arg)}
    foreach($key in @('MYSQL_PWD','KG_I2_ADMIN_PASSWORD','KG_I2_LOADER_PASSWORD','KG_I2_READER_PASSWORD','KG_I2_AUTHORIZED')){[void]$start.Environment.Remove($key)}
    if($Real){$start.Environment['KG_I2_AUTHORIZED']=$expectedUuid;$start.Environment['KG_I2_READER_PASSWORD']=$readerPassword}
    $started=Get-Date
    Write-Output "Starting $Suite; evidence=$runId"
    $child=[Diagnostics.Process]::Start($start)
    $out=$child.StandardOutput.ReadToEndAsync();$err=$child.StandardError.ReadToEndAsync()
    try{
        $deadline=(Get-Date).AddMinutes(5)
        while(-not $child.WaitForExit(1000)){if((Get-Date) -gt $deadline){$child.Kill($true);throw 'Bounded test process exceeded five minutes'}}
        $log=Redact ($out.GetAwaiter().GetResult()+"`n"+$err.GetAwaiter().GetResult())
        $logPath=Join-Path $reportRoot "$Suite-MAVEN.log"
        if(Test-Path -LiteralPath $logPath){throw 'Test log already exists'}
        [IO.File]::WriteAllText($logPath,$log,$utf8)
        $classes=switch($Suite){
            'Original' {$base=Get-Content -LiteralPath (Join-Path $projectRoot 'docs/assistant-team/integration/I1/I1-BASELINE.json') -Raw|ConvertFrom-Json;@($base.files|Where-Object kind -eq 'test'|ForEach-Object{$_.file.Replace('backend-java/src/test/java/','').Replace('/','.').Replace('.java','')})}
            'Ranking' {@('com.knowledgegraph.assistant.quality.JdbcQualityInsightRankingRegressionTest')}
            'IsolatedCount' {@('com.knowledgegraph.assistant.quality.JdbcQualityInsightIsolatedCountRegressionTest')}
            'WholeRegression' {@('com.knowledgegraph.assistant.quality.JdbcQualityInsightPerformanceRegressionTest')}
            'Matrix' {@('com.knowledgegraph.assistant.i2.I2QualityInsightWholeTest')}
            'CountPlan' {@('com.knowledgegraph.assistant.i2.I2QualityIsolatedCountTest')}
            'Base' {@('com.knowledgegraph.assistant.i2.I2JdbcBaselineTest','com.knowledgegraph.assistant.i2.I2ReadOnlyPrivilegeTest')}
            'Budget' {@('com.knowledgegraph.assistant.i2.I2QueryBudgetTest')}
            'LegacyWindow' {@('com.knowledgegraph.assistant.i2.I2ScaleQueryTest')}
            default {@('com.knowledgegraph.assistant.i2.I2QualityInsightRankingTest')}
        }
        $suites=@();$keys=[Collections.Generic.HashSet[string]]::new();$definitions=[Collections.Generic.HashSet[string]]::new()
        foreach($class in $classes){
            $path=Join-Path $projectRoot "backend-java/target/surefire-reports/TEST-$class-$runId-$Suite.xml"
            if(-not(Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).LastWriteTime -lt $started.AddSeconds(-2)){continue}
            $xml=[xml][IO.File]::ReadAllText($path)
            $cases=@($xml.testsuite.testcase|ForEach-Object{[void]$keys.Add($_.classname+'#'+$_.name);[void]$definitions.Add($_.classname+'#'+($_.name -replace '\[\d+\]$',''));@{name=$_.name;class=$_.classname;seconds=$_.time;failure=(Redact ([string]$_.failure.message));error=(Redact ([string]$_.error.message))}})
            $suites+=@{class=$class;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped;cases=$cases;xmlPath=$path;xmlRawSha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()}
        }
        $records=@($log -split '\r?\n'|Where-Object{($_.StartsWith('R1_EVIDENCE ') -or $_.StartsWith('R2_EVIDENCE ') -or $_.StartsWith('R3_EVIDENCE '))}|ForEach-Object{ $_.Substring(12)|ConvertFrom-Json })
        Save-New "$Suite-RESULT.json" @{suite=$Suite;startedAt=$started.ToString('o');exitCode=$child.ExitCode;suites=$suites;uniqueTestCases=$keys.Count;uniqueMethodDefinitions=$definitions.Count;executionRound=1;reportSuffix=($runId+'-'+$Suite);records=$records;rawXmlArchived=$false}
        Write-Output ($log -split '\r?\n'|Where-Object{$_ -match 'Tests run:|BUILD (SUCCESS|FAILURE)|\[ERROR\]'})
        $expected=switch($Suite){'Original'{154}'Ranking'{15}'IsolatedCount'{10}'WholeRegression'{19}'Base'{35}'Budget'{3}'LegacyWindow'{1}default{1}}
        if($child.ExitCode -ne 0 -or $suites.Count -ne $classes.Count -or $keys.Count -ne $expected -or @($suites|Where-Object{$_.failures -ne 0 -or $_.errors -ne 0 -or $_.skipped -ne 0}).Count){$exception=[Exception]::new("R3 $Suite validation failed; preserve batch evidence without field-code changes");$exception.Data['TestFailure']=$true;throw $exception}
    }finally{if(-not $child.HasExited){$child.Kill($true)};$child.Dispose()}
}
function Reader-Window([string]$Suite){
    $reportPrefix='R3_'+$Suite
    $before=Snapshot-I2 'BEFORE'
    if($before.tables.Count -ne 34 -or $before.loaderLocked -ne 'Y'){throw 'Reader window requires original 34 tables and locked loader'}
    $testSucceeded=$false
    [void](Invoke-I2Sql 'root' $adminPassword 'SET GLOBAL general_log=ON;')
    try{Run-TestGroup $Suite $true;$testSucceeded=$true}
    catch{
        if($_.Exception.Data['TestFailure']){$failedSuites.Add($Suite);Write-Output "Recorded $Suite failure; continuing only independent approved diagnostic groups"}
        else{throw}
    }
    finally{
        [void](Invoke-I2Sql 'root' $adminPassword 'SET GLOBAL general_log=OFF;')
        $after=Snapshot-I2 'AFTER'
        $same=($before.tables|ConvertTo-Json -Depth 5 -Compress) -ceq ($after.tables|ConvertTo-Json -Depth 5 -Compress)
        $binlogSame=$before.binaryLogStatus -ceq $after.binaryLogStatus
        $active=[int](Invoke-I2Sql 'root' $adminPassword "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE USER='kg_i2_reader' AND COMMAND<>'Sleep';")
        Save-New "$Suite-ZERO-WRITE.json" @{suite=$Suite;testSucceeded=$testSucceeded;tableCount=$before.tables.Count;dataAndSchemaEqual=$same;binlogPositionEqual=$binlogSame;loaderLockedBefore=$before.loaderLocked;loaderLockedAfter=$after.loaderLocked;activeReaderQueriesAfter=$active;generalLogRawSha256=(Get-FileHash -LiteralPath (Join-Path $envRoot 'mysql-general.log')).Hash.ToLowerInvariant();testProcessOnlyReaderCredential=$true}
        if(-not $same -or -not $binlogSame -or $after.loaderLocked -ne 'Y' -or $active -ne 0){throw 'Reader window invariant failed; stop, never reset evidence'}
    }
}
$failedSuites=[Collections.Generic.List[string]]::new()
$server=$null;$ownedServerId=$null;$identityVerified=$false;$adminPassword='';$readerPassword='';$failure=$null;$graceful=$false
try{
    if($Phase -eq 'Offline'){
        Run-TestGroup 'Original' $false
        Run-TestGroup 'Ranking' $false
        Run-TestGroup 'IsolatedCount' $false
        Run-TestGroup 'WholeRegression' $false
    }else{
        $junction=Get-Item -LiteralPath $aliasRoot
        if($junction.LinkType -ne 'Junction' -or $junction.ResolveLinkTarget($true).FullName -ne $envRoot){throw 'Original junction mismatch'}
        if(@(Get-NetTCPConnection -LocalPort 33078 -State Listen -ErrorAction SilentlyContinue).Count){throw 'Port occupied; do not connect'}
        if(@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'"|Where-Object{$_.ExecutablePath -like "$aliasRoot\*" -or $_.ExecutablePath -like "$envRoot\*"}).Count){throw 'Original isolated process already exists; do not take over'}
        if(-not [IO.File]::ReadAllText((Join-Path $envRoot 'mysql-data/auto.cnf')).Contains("server-uuid=$expectedUuid")){throw 'Original data UUID mismatch'}
        $credentialPath=Join-Path $envRoot 'credentials.dpapi.json'
        $acl=Get-Acl -LiteralPath $credentialPath
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        if(-not $acl.AreAccessRulesProtected -or @($acl.Access).Count -ne 1 -or $acl.Access[0].IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -ne $sid){throw 'Credential ACL mismatch'}
        $saved=Get-Content -LiteralPath $credentialPath -Raw|ConvertFrom-Json
        if($saved.serverUuid -ne $expectedUuid -or $saved.database -ne $db){throw 'Credential instance mismatch'}
        $adminPassword=Unprotect $saved.admin;$readerPassword=Unprotect $saved.reader
        $secrets.Add($adminPassword);$secrets.Add($readerPassword)
        $server=Start-Process -FilePath (Join-Path $bin 'mysqld.exe') -ArgumentList @('--defaults-file=D:/kg-i2-env/mysql-i2-ascii.ini','--console') -WorkingDirectory $aliasRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $reportRoot 'server-stdout.log') -RedirectStandardError (Join-Path $reportRoot 'server-stderr.log') -PassThru
        for($i=0;$i -lt 40;$i++){
            if($server.HasExited){throw 'Owned MySQL exited during startup'}
            $listeners=@(Get-NetTCPConnection -LocalPort 33078 -State Listen -ErrorAction SilentlyContinue)
            if($listeners.Count){
                $candidate=$listeners[0].OwningProcess;$process=Get-CimInstance Win32_Process -Filter "ProcessId=$candidate"
                $executables=@((Join-Path $bin 'mysqld.exe').Replace('/','\'),(Join-Path $envRoot 'mysql-8.4.11-winx64/bin/mysqld.exe').Replace('/','\'))
                $pidFile=Join-Path $envRoot 'mysql-i2.pid'
                if(($candidate -ne $server.Id -and $process.ParentProcessId -ne $server.Id) -or $process.ExecutablePath -notin $executables -or -not $process.CommandLine.Contains('--defaults-file=D:/kg-i2-env/mysql-i2-ascii.ini') -or -not(Test-Path -LiteralPath $pidFile) -or [IO.File]::ReadAllText($pidFile).Trim() -ne [string]$candidate -or @($listeners|Where-Object{$_.LocalAddress -ne '127.0.0.1' -or $_.OwningProcess -ne $candidate}).Count){throw 'Listener process identity mismatch'}
                $ownedServerId=$candidate;break
            }
            Start-Sleep -Milliseconds 250
        }
        if(-not $ownedServerId){throw 'Original instance did not become ready'}
        $id=Invoke-I2Sql 'root' $adminPassword 'SELECT JSON_OBJECT("version",VERSION(),"uuid",@@server_uuid,"port",@@port,"datadir",@@datadir,"currentUser",CURRENT_USER(),"memory",@@shared_memory_base_name);'|ConvertFrom-Json
        if($id.version -ne '8.4.11' -or $id.uuid -ne $expectedUuid -or $id.port -ne 33078 -or $id.datadir.Replace('\','/').TrimEnd('/') -notin @('D:/kg-i2-env/mysql-data','D:/知识图谱-i2-env/mysql-data') -or $id.currentUser -ne 'root@localhost' -or $id.memory -ne 'KG_I2_33078_8c6cfcb3'){throw 'Connected instance identity mismatch'}
        $identityVerified=$true
        $scaleCounts=Invoke-I2Sql 'kg_i2_reader' $readerPassword "SELECT (SELECT COUNT(*) FROM library_nodes WHERE library_id=7901),(SELECT COUNT(*) FROM knowledge_edges e JOIN library_nodes s ON s.node_id=e.source_node_id AND s.library_id=7901 JOIN library_nodes t ON t.node_id=e.target_node_id AND t.library_id=7901 WHERE e.status='active'),(SELECT COUNT(*) FROM documents WHERE library_id=7901),(SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id=c.unit_id JOIN documents d ON d.id=u.document_id WHERE d.library_id=7901);" $db
        if($scaleCounts -ne "10000`t30000`t2000`t20000"){throw 'Original scale changed; no reset allowed'}
        $grants=Invoke-I2Sql 'kg_i2_reader' $readerPassword 'SELECT CURRENT_USER(),CURRENT_ROLE(); SHOW GRANTS;' $db
        $expectedGrants=(Get-Content -LiteralPath (Join-Path (Split-Path $r3) 'I2-ENVIRONMENT.json') -Raw|ConvertFrom-Json).readerGrants
        if($grants.Replace("`r`n","`n").TrimEnd() -cne $expectedGrants.Replace("`r`n","`n").TrimEnd()){throw 'Reader grants changed'}
        if((Invoke-I2Sql 'root' $adminPassword "SELECT account_locked FROM mysql.user WHERE User='kg_i2_loader' AND Host='127.0.0.1';") -ne 'Y'){throw 'Loader unexpectedly unlocked'}
        Save-New 'IDENTITY.json' @{identity=$id;scaleCounts=$scaleCounts;readerGrants=$grants;loaderLocked=$true;originalCredentialFileUsed=$true;credentialAclCurrentUserOnly=$true}
        # No sample loading, account change, index change or reset in R2.
        $reportPrefix='R3_ENTRY'
        $entry=Snapshot-I2 'BEFORE'
        $last=Get-Content -LiteralPath (Join-Path $projectRoot 'docs/assistant-team/integration/I2/R2/evidence/ExplainList-20260929T003739745/I2-R2_LIST_EXPLAIN-AFTER.json') -Raw|ConvertFrom-Json
        $same=($entry.tables|ConvertTo-Json -Depth 5 -Compress) -ceq ($last.tables|ConvertTo-Json -Depth 5 -Compress)
        Save-New 'RETAINED-DATA.json' @{tableCount=$entry.tables.Count;matchesLastR2DataAndDDL=$same;loaderLocked=$entry.loaderLocked;fixturesReloaded=$false;binlogRestartPositionNotComparedAcrossRuns=$true}
        if(-not $same -or $entry.tables.Count -ne 34 -or $entry.loaderLocked -ne 'Y'){throw 'Retained R2 data/DDL mismatch; do not reset'}
        foreach($suite in @('Matrix','CountPlan','Plan','Semantics','Base','Budget','Performance','LegacyWindow')){
            if(($suite -eq 'Semantics' -and $failedSuites.Contains('Matrix')) -or
               ($suite -eq 'Performance' -and ($failedSuites.Contains('Matrix') -or $failedSuites.Contains('Semantics')))){
                Save-New "$suite-NOT-RUN.json" @{suite=$suite;reason='Full-call/query correctness or execution already failed; no useful performance acceptance possible';samplesExecuted=0}
                continue
            }
            Reader-Window $suite
        }
        if($failedSuites.Count){throw ("R3 batch has validation failures: "+($failedSuites -join ', '))}
    }
}catch{
    $failure=Redact $_.Exception.Message
    Save-New 'FAILURE.json' @{phase=$Phase;message=$failure;stoppedAt=(Get-Date).ToString('o');noAutomaticNextGroup=$true}
    Write-Output "R3 STOP: $failure"
}finally{
    if($Phase -eq 'Real' -and $server){
        if($identityVerified){
            try{[void](Invoke-I2Sql 'root' $adminPassword 'SET GLOBAL general_log=OFF;');[void](Invoke-I2Sql 'root' $adminPassword 'SHUTDOWN;');$graceful=$true}
            catch{Write-Warning 'Graceful shutdown not confirmed; stopping verified owned process only'}
        }
        if(-not $graceful){if($ownedServerId){Stop-Process -Id $ownedServerId -ErrorAction SilentlyContinue};if(-not $server.HasExited){Stop-Process -Id $server.Id -ErrorAction SilentlyContinue}}
        [void]$server.WaitForExit(10000)
        $remaining=@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'"|Where-Object{$_.ExecutablePath -like "$aliasRoot\*" -or $_.ExecutablePath -like "$envRoot\*"}|Select-Object ProcessId,ExecutablePath)
        $remainingListeners=@(Get-NetTCPConnection -LocalPort 33078 -State Listen -ErrorAction SilentlyContinue|Select-Object LocalAddress,LocalPort,OwningProcess)
        Save-New 'SHUTDOWN.json' @{graceful=$graceful;remainingProcesses=$remaining;remainingListeners=$remainingListeners;dataRetained=$true;existingServicesUntouched=$true}
        if(-not $graceful -or $remaining.Count -or $remainingListeners.Count){$failure='Isolated shutdown not fully verified'}
    }
    try { Save-New 'BASELINE-AFTER.json' (& (Join-Path $r3 'Verify-R3Baseline.ps1') -RequirePackage) }
    catch { Save-New 'BASELINE-AFTER-ERROR.json' @{message=$_.Exception.Message}; if(-not $failure){$failure='Post-run protection failed'} }
    Save-New 'RUN.json' @{runId=$runId;phase=$Phase;success=($null -eq $failure);failure=$failure;failedSuites=@($failedSuites);finishedAt=(Get-Date).ToString('o');noI2CompletionClaim=$true}
}
if($failure){throw $failure}
Write-Output "R3 $Phase completed; evidence=$reportRoot"
