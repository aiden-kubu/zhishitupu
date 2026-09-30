[CmdletBinding()]
param([ValidateSet('Probe','Bootstrap','Inspect','LoadFixture','TestBase','Variants','Scale','ExplainScale','AnalyzeScale')][string]$Phase = 'Probe')
$ErrorActionPreference = 'Stop'
$envRoot = 'D:\知识图谱-i2-env'
$projectRoot = 'D:\知识图谱'
$reportRoot = Join-Path $projectRoot 'docs/assistant-team/integration/I2'
$aliasRoot = 'D:\kg-i2-env'
$bin = Join-Path $aliasRoot 'mysql-8.4.11-winx64/bin'
$db = 'kg_assistant_i2_20260927_r01'
$expectedUuid = '8c6cfcb3-ba49-11f1-bf64-005056c00001'
$credentialFile = Join-Path $envRoot 'credentials.dpapi.json'
$utf8 = [Text.UTF8Encoding]::new($false)
$secrets = [Collections.Generic.List[string]]::new()
function New-Password { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)) }
function Redact([string]$Text) {
    foreach ($secret in $secrets) { if ($secret) { $Text = $Text.Replace($secret,'[REDACTED]') } }
    return $Text
}
function Invoke-I2Sql([string]$User, [string]$Password, [string]$Sql, [string]$Database = '') {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = Join-Path $bin 'mysql.exe'
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $start.StandardInputEncoding = $utf8; $start.StandardOutputEncoding = $utf8; $start.StandardErrorEncoding = $utf8
    $transport = if ($User -eq 'root') { @('--protocol=MEMORY','--host=localhost','--shared-memory-base-name=KG_I2_33078_8c6cfcb3') } else { @('--protocol=TCP','--host=127.0.0.1','--port=33078') }
    foreach ($arg in (@('--no-defaults','--no-login-paths') + $transport + @('--connect-timeout=3','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names', "--user=$User"))) {
        [void]$start.ArgumentList.Add($arg)
    }
    if ($Database) { [void]$start.ArgumentList.Add("--database=$Database") }
    [void]$start.Environment.Remove('MYSQL_PWD')
    if ($Password) { $start.Environment['MYSQL_PWD'] = $Password }
    $process = [Diagnostics.Process]::Start($start)
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write($Sql); $process.StandardInput.Close()
    if (-not $process.WaitForExit(45000)) { $process.Kill(); throw 'Bounded I2 mysql client timed out' }
    $text = $stdout.GetAwaiter().GetResult(); $err = $stderr.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw ("MySQL client rejected operation: " + (Redact $err)) }
    return $text.TrimEnd()
}
function Unprotect([string]$Value) {
    return [Net.NetworkCredential]::new('', (ConvertTo-SecureString $Value)).Password
}
function Save-Credentials([string]$Admin, [string]$Reader) {
    # Generated DPAPI secret artifact outside repository. Loader password is never persisted.
    $data = @{admin=(ConvertFrom-SecureString (ConvertTo-SecureString $Admin -AsPlainText -Force));reader=(ConvertFrom-SecureString (ConvertTo-SecureString $Reader -AsPlainText -Force));serverUuid=$expectedUuid;database=$db}
    [IO.File]::WriteAllText($credentialFile,($data | ConvertTo-Json),$utf8)
    $acl = [Security.AccessControl.FileSecurity]::new(); $acl.SetAccessRuleProtection($true,$false)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetOwner($sid); $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','Allow'))
    Set-Acl -LiteralPath $credentialFile -AclObject $acl
}
$server = $null; $ownedServerId = $null; $adminPassword = ''; $readerPassword = ''; $bootstrapPasswordChanged = $false
try {
    $protection = Get-Content -LiteralPath (Join-Path $reportRoot 'I2-PROTECTION-BEFORE.json') -Raw | ConvertFrom-Json
    foreach ($entry in $protection.files) {
        $path = Join-Path $projectRoot $entry.file
        if (-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $entry.rawSha256) { throw "Protected file changed; stop: $($entry.file)" }
    }
    $junction = Get-Item -LiteralPath $aliasRoot -Force
    if ($junction.LinkType -ne 'Junction' -or $junction.ResolveLinkTarget($true).FullName -ne $envRoot) { throw 'Approved junction target mismatch' }
    if (@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -eq 33078).Count -ne 0) { throw 'Port occupied; do not connect to an unowned process' }
    $auto = [IO.File]::ReadAllText((Join-Path $envRoot 'mysql-data/auto.cnf'))
    if (-not $auto.Contains("server-uuid=$expectedUuid")) { throw 'Data directory UUID mismatch' }
    if ((Get-FileHash -LiteralPath (Join-Path $aliasRoot 'mysql-data/auto.cnf')).Hash -ne (Get-FileHash -LiteralPath (Join-Path $envRoot 'mysql-data/auto.cnf')).Hash) { throw 'Alias does not expose original data directory' }
    if ($Phase -eq 'Bootstrap') {
        if (Test-Path -LiteralPath $credentialFile) { throw 'Bootstrap credentials exist; do not overwrite' }
    } elseif (Test-Path -LiteralPath $credentialFile) {
        $saved = [IO.File]::ReadAllText($credentialFile) | ConvertFrom-Json
        if ($saved.serverUuid -ne $expectedUuid -or $saved.database -ne $db) { throw 'Credential environment mismatch' }
        $adminPassword = Unprotect $saved.admin; $readerPassword = Unprotect $saved.reader
        $secrets.Add($adminPassword); $secrets.Add($readerPassword)
    }
    $launchTag = (Get-Date).ToString('yyyyMMdd-HHmmss-fff')
    $server = Start-Process -FilePath (Join-Path $bin 'mysqld.exe') -ArgumentList @('--defaults-file=D:/kg-i2-env/mysql-i2-ascii.ini','--console') -WorkingDirectory $aliasRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $envRoot "memory-startup-$launchTag-stdout.txt") -RedirectStandardError (Join-Path $envRoot "memory-startup-$launchTag-stderr.txt") -PassThru
    $ready = $false
    for ($i=0; $i -lt 40; $i++) {
        if ($server.HasExited) { throw 'Owned MySQL process exited during startup; inspect isolated error log' }
        $listeners = @(Get-NetTCPConnection -LocalPort 33078 -State Listen -ErrorAction SilentlyContinue)
        if ($listeners.Count -gt 0) {
            $ownedServerId = $listeners[0].OwningProcess
            $child = Get-CimInstance Win32_Process -Filter "ProcessId=$ownedServerId"
            $allowedExecutables = @((Join-Path $bin 'mysqld.exe').Replace('/','\'),(Join-Path $envRoot 'mysql-8.4.11-winx64/bin/mysqld.exe').Replace('/','\'))
            # Windows mysqld may use an owned launcher plus a separate server process.
            # Accept only that direct parent/child relationship, not merely a matching port.
            $ownedRelationship = $ownedServerId -eq $server.Id -or $child.ParentProcessId -eq $server.Id
            $pidFile = Join-Path $envRoot 'mysql-i2.pid'
            $pidMatches = (Test-Path -LiteralPath $pidFile) -and [IO.File]::ReadAllText($pidFile).Trim() -eq [string]$ownedServerId
            if (-not $ownedRelationship -or -not $pidMatches -or $child.ExecutablePath -notin $allowedExecutables -or -not $child.CommandLine.Contains('--defaults-file=D:/kg-i2-env/mysql-i2-ascii.ini') -or @($listeners | Where-Object { $_.OwningProcess -ne $ownedServerId -or $_.LocalAddress -ne '127.0.0.1' }).Count -gt 0) { $ownedServerId = $null; throw 'Listener identity/address mismatch; do not connect' }
            $ready = $true; break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw 'Owned instance failed to become ready in time' }
    $identity = Invoke-I2Sql 'root' $adminPassword 'SELECT JSON_OBJECT("version",VERSION(),"uuid",@@server_uuid,"port",@@port,"datadir",@@datadir,"partialRevokes",@@partial_revokes,"currentUser",CURRENT_USER(),"sharedMemory",@@shared_memory,"sharedMemoryName",@@shared_memory_base_name);'
    $id = $identity | ConvertFrom-Json
    if ($id.version -ne '8.4.11' -or $id.uuid -ne $expectedUuid -or $id.port -ne 33078 -or $id.partialRevokes -ne 1) { throw 'Connected identity does not match approval' }
    if ($id.datadir.Replace('\','/').TrimEnd('/') -notin @('D:/kg-i2-env/mysql-data','D:/知识图谱-i2-env/mysql-data')) { throw 'Connected data directory differs from original instance' }
    if ($id.currentUser -ne 'root@localhost' -or $id.sharedMemory -ne 1 -or $id.sharedMemoryName -ne 'KG_I2_33078_8c6cfcb3') { throw 'Management identity or named local channel mismatch' }
    Write-Output "Owned MySQL verified: version=$($id.version) uuid=$($id.uuid) port=$($id.port)"
    if ($Phase -eq 'Probe') {
        $proof = @{stage='I2';phase='same_instance_alias_start_verified';checkedAt=(Get-Date).ToString('o');junction=$aliasRoot;resolvedTarget=$junction.ResolveLinkTarget($true).FullName;identity=$id;listeners=@($listeners | Select-Object LocalAddress,LocalPort,OwningProcess);processId=$server.Id;autoCnfSha256=(Get-FileHash -LiteralPath (Join-Path $envRoot 'mysql-data/auto.cnf')).Hash.ToLowerInvariant();reinitialized=$false;acceptanceDatabaseCreated=$false}
        [IO.File]::WriteAllText((Join-Path $reportRoot 'I2-MANAGEMENT-IDENTITY.json'),($proof | ConvertTo-Json -Depth 8),$utf8)
        Write-Output 'Same initialized instance verified through approved junction; probe performs no acceptance schema/account/fixture writes.'
    } elseif ($Phase -eq 'Bootstrap') {
        $newAdminPassword = New-Password; $readerPassword = New-Password; $loaderPassword = New-Password
        $secrets.Add($newAdminPassword); $secrets.Add($readerPassword); $secrets.Add($loaderPassword)
        [void](Invoke-I2Sql 'root' '' "ALTER USER 'root'@'localhost' IDENTIFIED BY '$newAdminPassword';")
        $adminPassword = $newAdminPassword
        $bootstrapPasswordChanged = $true
        Save-Credentials $adminPassword $readerPassword
        [void](Invoke-I2Sql 'root' $adminPassword @"
CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'kg_i2_loader'@'127.0.0.1' IDENTIFIED BY '$loaderPassword';
CREATE USER 'kg_i2_reader'@'127.0.0.1' IDENTIFIED BY '$readerPassword' WITH MAX_USER_CONNECTIONS 8;
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,DROP,INDEX,REFERENCES ON $db.* TO 'kg_i2_loader'@'127.0.0.1';
"@)
        $migrations = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'backend-java/src/main/resources/db/migration') -Filter 'V*.sql' | Sort-Object { [int]([regex]::Match($_.Name,'^V(\d+)').Groups[1].Value) })
        if ($migrations.Count -ne 11) { throw 'Migration count differs from V1-V11' }
        $executed = @()
        foreach ($migration in $migrations) {
            Write-Output "Applying unmodified migration: $($migration.Name)"
            [void](Invoke-I2Sql 'kg_i2_loader' $loaderPassword ([IO.File]::ReadAllText($migration.FullName,$utf8)) $db)
            $executed += @{file=$migration.Name;rawSha256=(Get-FileHash -LiteralPath $migration.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
        }
        $readTables = @('knowledge_libraries','library_aliases','knowledge_nodes','node_aliases','library_nodes','knowledge_edges','node_evidence','edge_evidence','documents','document_metadata','document_tags','document_topic_assignments','document_units','document_chunks','ingestion_jobs','entity_candidates','relation_candidates','extraction_batch_results','learning_paths','learning_path_steps','learning_assessment_attempts')
        foreach ($table in $readTables) { [void](Invoke-I2Sql 'root' $adminPassword "GRANT SELECT ON $db.$table TO 'kg_i2_reader'@'127.0.0.1';") }
        [void](Invoke-I2Sql 'root' $adminPassword @"
GRANT SELECT(enabled,vision,encrypted_api_key) ON $db.llm_profiles TO 'kg_i2_reader'@'127.0.0.1';
GRANT SELECT(setting_key,encrypted_value,is_secret) ON $db.system_settings TO 'kg_i2_reader'@'127.0.0.1';
ALTER USER 'kg_i2_loader'@'127.0.0.1' ACCOUNT LOCK;
"@)
        $tables = Invoke-I2Sql 'root' $adminPassword "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' ORDER BY TABLE_NAME;"
        $columns = Invoke-I2Sql 'root' $adminPassword "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$db' ORDER BY TABLE_NAME,ORDINAL_POSITION;"
        $constraints = Invoke-I2Sql 'root' $adminPassword "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='$db' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,ORDINAL_POSITION;"
        $grants = Invoke-I2Sql 'kg_i2_reader' $readerPassword 'SELECT CURRENT_USER(),CURRENT_ROLE(); SHOW GRANTS;' $db
        $public = @{stage='I2';phase='schema_prepared_no_fixture_loaded';createdAt=(Get-Date).ToString('o');identity=$id;database=$db;migrations=$executed;readTables=$readTables;readerGrants=$grants;tableNames=$tables;columns=$columns;foreignKeys=$constraints;loaderLocked=$true;modelConnected=$false;appStarted=$false}
        [IO.File]::WriteAllText((Join-Path $reportRoot 'I2-ENVIRONMENT.json'),($public | ConvertTo-Json -Depth 8),$utf8)
        Write-Output 'V1-V11 applied; SELECT grants installed; loader locked; no fixture loaded.'
    } elseif ($Phase -eq 'LoadFixture') {
        $counts = Invoke-I2Sql 'root' $adminPassword "SELECT (SELECT COUNT(*) FROM $db.documents),(SELECT COUNT(*) FROM $db.knowledge_nodes),(SELECT COUNT(*) FROM $db.knowledge_libraries),(SELECT COUNT(*) FROM $db.knowledge_edges);"
        if ($counts -ne "0`t2`t1`t1") { throw 'Expected untouched V3-only acceptance schema; no reset/overwrite allowed' }
        $loaderPassword = New-Password; $secrets.Add($loaderPassword)
        [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' IDENTIFIED BY '$loaderPassword' ACCOUNT UNLOCK;")
        try {
            $fixtureSql = & (Join-Path $reportRoot 'Build-I2Fixture.ps1')
            [IO.File]::WriteAllText((Join-Path $reportRoot 'I2-FIXTURE.sql'),$fixtureSql,$utf8)
            [void](Invoke-I2Sql 'kg_i2_loader' $loaderPassword $fixtureSql $db)
        } finally {
            [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' ACCOUNT LOCK;")
        }
        $counts = Invoke-I2Sql 'kg_i2_reader' $readerPassword 'SELECT (SELECT COUNT(*) FROM documents),(SELECT COUNT(*) FROM document_units),(SELECT COUNT(*) FROM document_chunks),(SELECT COUNT(*) FROM knowledge_nodes),(SELECT COUNT(*) FROM knowledge_edges),(SELECT COUNT(*) FROM learning_paths),(SELECT COUNT(*) FROM learning_path_steps),(SELECT COUNT(*) FROM learning_assessment_attempts);' $db
        if ($counts -ne "6`t4`t5`t5`t3`t1`t2`t2") { throw 'Loaded fixture counts differ from frozen sample' }
        $proof = @{stage='I2';phase='base_fixture_loaded';checkedAt=(Get-Date).ToString('o');identity=$id;countsTsv=$counts;loaderLocked=$true;fixtureSqlSha256=(Get-FileHash -LiteralPath (Join-Path $reportRoot 'I2-FIXTURE.sql')).Hash.ToLowerInvariant();constraintsDisabled=$false}
        [IO.File]::WriteAllText((Join-Path $reportRoot 'I2-FIXTURE-LOAD.json'),($proof | ConvertTo-Json -Depth 8),$utf8)
        Write-Output 'Frozen I1 fixture mapped and loaded: 6 documents, 4 units, 5 chunks, 5 nodes, 3 edges, 1 path, 2 steps, 2 legacy attempts. Loader locked.'
    } elseif ($Phase -eq 'TestBase') {
        & (Join-Path $reportRoot 'Invoke-I2BaseVerification.ps1')
    } elseif ($Phase -eq 'Variants') {
        & (Join-Path $reportRoot 'Invoke-I2Variants.ps1')
    } elseif ($Phase -eq 'Scale') {
        & (Join-Path $reportRoot 'Invoke-I2Scale.ps1')
    } elseif ($Phase -eq 'ExplainScale') {
        # Read-only diagnostics for the exact timed-out query. No EXPLAIN ANALYZE or SQL rewrite.
        $query=@'
WITH visible_nodes AS (SELECT n.id,n.canonical_name,n.node_type FROM knowledge_nodes n WHERE 1=1 AND EXISTS (SELECT 1 FROM library_nodes ln WHERE ln.node_id=n.id AND ln.library_id IN (7901))), visible_edges AS (SELECT e.id,e.source_node_id,e.target_node_id FROM knowledge_edges e JOIN visible_nodes s ON s.id=e.source_node_id JOIN visible_nodes t ON t.id=e.target_node_id WHERE e.status='active') SELECT n.id,n.canonical_name,n.node_type,(SELECT COUNT(*) FROM visible_edges e WHERE e.source_node_id=n.id OR e.target_node_id=n.id) AS degree FROM visible_nodes n ORDER BY degree DESC,n.id LIMIT 10
'@
        $jsonPlan=Invoke-I2Sql 'kg_i2_reader' $readerPassword ("EXPLAIN FORMAT=JSON "+$query) $db
        $tabular=Invoke-I2Sql 'kg_i2_reader' $readerPassword ("EXPLAIN "+$query) $db
        $indexes=Invoke-I2Sql 'kg_i2_reader' $readerPassword 'SHOW INDEX FROM knowledge_edges; SHOW INDEX FROM library_nodes;' $db
        $nodeEvidenceDdl=Invoke-I2Sql 'kg_i2_reader' $readerPassword 'SHOW CREATE TABLE node_evidence;' $db
        $proof=@{stage='I2';kind='read_only_explain_not_query_execution_or_optimization';identity=$id;query=$query;jsonPlan=($jsonPlan|ConvertFrom-Json);tabularPlan=$tabular;indexes=$indexes;nodeEvidenceDdl=$nodeEvidenceDdl;readerUsed=$true;analyzeExecuted=$false;schemaChanged=$false;frozenCodeChanged=$false}
        $explainFile=if(Test-Path -LiteralPath (Join-Path $reportRoot 'I2-SCALE-STATISTICS.json')){'I2-SCALE-EXPLAIN-ANALYZED.json'}else{'I2-SCALE-EXPLAIN.json'}
        [IO.File]::WriteAllText((Join-Path $reportRoot $explainFile),($proof|ConvertTo-Json -Depth 30),$utf8)
        Write-Output $tabular
    } elseif ($Phase -eq 'AnalyzeScale') {
        # Fixture-preparation maintenance in the disposable I2 DB only. No row/schema/index changes.
        $loaderPassword=New-Password; $secrets.Add($loaderPassword)
        $beforeStats=Invoke-I2Sql 'root' $adminPassword "SELECT TABLE_NAME,TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' ORDER BY TABLE_NAME;"
        [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' IDENTIFIED BY '$loaderPassword' ACCOUNT UNLOCK;")
        try { $analyzed=Invoke-I2Sql 'kg_i2_loader' $loaderPassword 'ANALYZE TABLE knowledge_nodes,knowledge_edges,library_nodes,node_aliases,node_evidence,edge_evidence,documents,document_units,document_chunks,document_metadata,document_topic_assignments,entity_candidates,relation_candidates,ingestion_jobs;' $db }
        finally { [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' ACCOUNT LOCK;") }
        $statsProof=@{kind='I2_loader_fixture_optimizer_statistics_maintenance_not_business_write';before=$beforeStats;analyzeOutput=$analyzed;frozenSqlChanged=$false;indexChanged=$false;timeoutChanged=$false;loaderLocked=$true}
        [IO.File]::WriteAllText((Join-Path $reportRoot 'I2-SCALE-STATISTICS.json'),($statsProof|ConvertTo-Json -Depth 8),$utf8)
        Write-Output $analyzed
        & (Join-Path $reportRoot 'Invoke-I2BaseVerification.ps1') -Suite Scale -Case SCALE_ANALYZED
    } else {
        Write-Output (Invoke-I2Sql 'root' $adminPassword "SELECT TABLE_NAME,TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' ORDER BY TABLE_NAME;")
    }
} finally {
    if ($Phase -eq 'Bootstrap' -and $bootstrapPasswordChanged -and $ownedServerId) {
        try { [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER IF EXISTS 'kg_i2_loader'@'127.0.0.1' ACCOUNT LOCK;") }
        catch { Write-Warning 'Could not confirm loader lock; server will be stopped and no reader verification will run.' }
    }
    if ($server -and ((-not $server.HasExited) -or ($ownedServerId -and (Get-Process -Id $ownedServerId -ErrorAction SilentlyContinue)))) {
        try { if (-not $ownedServerId) { throw 'No verified owned listener; SQL shutdown prohibited' }; [void](Invoke-I2Sql 'root' $adminPassword 'SHUTDOWN;') }
        catch { Write-Warning 'Graceful shutdown failed; stopping only verified owned processes'; if ($ownedServerId) { Stop-Process -Id $ownedServerId -ErrorAction SilentlyContinue }; Stop-Process -Id $server.Id -ErrorAction SilentlyContinue }
        [void]$server.WaitForExit(10000)
    }
    # Credentials were persisted immediately after changing root's password.
    # Do not rewrite/reapply the protected file ACL during shutdown.
    $remainingOwned = @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.ExecutablePath -eq (Join-Path $bin 'mysqld.exe').Replace('/','\') })
    if ($remainingOwned.Count -gt 0) { Write-Warning 'An isolated MySQL process remains; do not claim shutdown succeeded.' }
    else { Write-Output 'Owned instance stopped. Data retained. No pre-existing service touched.' }
}
