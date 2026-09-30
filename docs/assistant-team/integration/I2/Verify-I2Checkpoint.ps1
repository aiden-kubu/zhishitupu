# Offline evidence review only; no DB connection, build, environment reset, or source changes.
$ErrorActionPreference='Stop'
$project='D:\知识图谱'
$report=Join-Path $project 'docs/assistant-team/integration/I2'
$utf8=[Text.UTF8Encoding]::new($false)
function NormalizedHash([string]$path) {
    $text=[IO.File]::ReadAllText($path,$utf8).Replace("`r`n","`n")
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($text))).ToLowerInvariant()
}
function Require([bool]$condition,[string]$message){if(-not $condition){throw $message}}
$protected=Get-Content -LiteralPath (Join-Path $report 'I2-PROTECTION-BEFORE.json') -Raw|ConvertFrom-Json
foreach($entry in $protected.files){Require ((Get-FileHash -LiteralPath (Join-Path $project $entry.file)).Hash.ToLowerInvariant() -eq $entry.rawSha256) "Protected file changed: $($entry.file)"}
$base=Get-Content -LiteralPath (Join-Path $project 'docs/assistant-team/integration/I1/I1-BASELINE.json') -Raw|ConvertFrom-Json
$copies=0;$frozen=0
foreach($entry in $base.files){
    Require ((NormalizedHash (Join-Path $project $entry.file)) -eq $entry.normalizedSha256) "Baseline changed: $($entry.file)"
    Require ((NormalizedHash $entry.source) -eq $entry.normalizedSha256) "Owner source changed: $($entry.source)"
    foreach($copy in $entry.identicalCopies){Require ((NormalizedHash $copy) -eq $entry.normalizedSha256) "Duplicate changed: $copy";$copies++}
}
foreach($spec in $base.frozenManifests){
    $manifest=Get-Content -LiteralPath (Join-Path $project $spec.file) -Raw|ConvertFrom-Json
    Require ($manifest.files.Count -eq $spec.expectedEntries) 'Frozen count changed'
    foreach($entry in $manifest.files){Require ((NormalizedHash (Join-Path $spec.targetRoot $entry.file)) -eq $entry.normalizedSha256) "Frozen dependency changed: $($entry.file)";$frozen++}
}
$keys=[Collections.Generic.HashSet[string]]::new();$suites=@()
foreach($entry in @($base.files|Where-Object kind -eq 'test')){
    $name=$entry.file.Replace('backend-java/src/test/java/','').Replace('/','.').Replace('.java','')
    $path=Join-Path $project "backend-java/target/surefire-reports/TEST-$name.xml"
    $xml=[xml][IO.File]::ReadAllText($path)
    Require ([int]$xml.testsuite.failures -eq 0 -and [int]$xml.testsuite.errors -eq 0 -and [int]$xml.testsuite.skipped -eq 0) "Regression failure: $name"
    foreach($case in $xml.testsuite.testcase){Require ($keys.Add($case.classname+'#'+$case.name)) 'Duplicate regression case'}
    $suites+=@{class=$name;tests=[int]$xml.testsuite.tests;xmlSha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant();lastWriteTime=(Get-Item -LiteralPath $path).LastWriteTime.ToString('o')}
}
Require ($keys.Count -eq 154 -and $suites.Count -eq 19) 'Regression count mismatch'
$windows=@()
foreach($file in Get-ChildItem -LiteralPath $report -Filter 'I2-*-ZERO-WRITE.json'){
    $proof=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json
    Require ($proof.dataAndSchemaEqual -and $proof.binlogPositionEqual -and $proof.loaderLockedBefore -eq 'Y' -and $proof.loaderLockedAfter -eq 'Y') "Zero-write proof failed: $($file.Name)"
    $windows+=@{file=$file.Name;tableCount=$proof.allTableCount;passed=$true;mavenExitCode=$proof.mavenExitCode}
}
$baseline=Get-Content -LiteralPath (Join-Path $report 'I2-BASE-AFTER.json') -Raw|ConvertFrom-Json
$restored=Get-Content -LiteralPath (Join-Path $report 'I2-BUDGET-AFTER.json') -Raw|ConvertFrom-Json
Require (($baseline.tables|ConvertTo-Json -Depth 5 -Compress) -ceq ($restored.tables|ConvertTo-Json -Depth 5 -Compress)) 'Base not restored after fault variants'
$newFiles=@(& git -C $project -c core.quotepath=false ls-files --cached --others --exclude-standard | Where-Object { $_ -notin $protected.files.file })
$unexpected=@($newFiles|Where-Object {-not $_.StartsWith('docs/assistant-team/integration/I2/') -and -not $_.StartsWith('backend-java/src/test/java/com/knowledgegraph/assistant/i2/')})
Require ($unexpected.Count -eq 0) "Unexpected new paths: $($unexpected -join ',')"
Require ((& git -C $project rev-parse HEAD).Trim() -eq $protected.head) 'HEAD changed'
Require ((& git -C $project branch --show-current).Trim() -eq $protected.branch) 'Branch changed'
$parseChecks=0
foreach($file in Get-ChildItem -LiteralPath $report -Filter '*.ps1'){
    $tokens=$null;$errors=$null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
    Require ($errors.Count -eq 0) "Script syntax error: $($file.Name)";$parseChecks++
}
$listeners=@(Get-NetTCPConnection -LocalPort 33078 -State Listen -ErrorAction SilentlyContinue)
$processes=@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'"|Where-Object {$_.ExecutablePath -like 'D:\kg-i2-env\*' -or $_.ExecutablePath -like 'D:\知识图谱-i2-env\*'}|Select-Object ProcessId,ExecutablePath)
Require ($listeners.Count -eq 0 -and $processes.Count -eq 0) 'Isolated server not stopped'
$acl=Get-Acl -LiteralPath 'D:\知识图谱-i2-env\credentials.dpapi.json'
Require ($acl.AreAccessRulesProtected -and @($acl.Access).Count -eq 1) 'Credential file ACL unexpected'
$currentSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
Require ($acl.Access[0].IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -eq $currentSid -and $acl.Access[0].AccessControlType -eq 'Allow') 'Credential ACL does not belong solely to current user'
$saved=Get-Content -LiteralPath 'D:\知识图谱-i2-env\credentials.dpapi.json' -Raw|ConvertFrom-Json
$secrets=@([Net.NetworkCredential]::new('',(ConvertTo-SecureString $saved.admin)).Password,[Net.NetworkCredential]::new('',(ConvertTo-SecureString $saved.reader)).Password)
$secretHits=0
foreach($file in Get-ChildItem -LiteralPath $report -File){
    $body=[IO.File]::ReadAllText($file.FullName)
    foreach($secret in $secrets){if($body.Contains($secret)){$secretHits++}}
}
Require ($secretHits -eq 0) 'Secret found in public I2 evidence; do not publish'
$result=@{stage='I2';status='incomplete_performance_blocker_not_user_acceptance';checkedAt=(Get-Date).ToString('o');protectedFilesMatched=$protected.files.Count;baselineFilesMatched=$base.files.Count;duplicateCopiesMatched=$copies;frozenEntriesMatched=$frozen;regressionTests=$keys.Count;regressionSuites=$suites;zeroWriteWindows=$windows;baseRestoredAfterVariants=$true;unexpectedNewPaths=$unexpected;scriptsSyntaxChecked=$parseChecks;credentialAclCurrentUserOnly=$true;publicEvidencePlaintextAdminReaderHits=$secretHits;isolatedProcesses=$processes;port33078Listeners=$listeners;newFiles=@($newFiles|ForEach-Object{@{file=$_;rawSha256=(Get-FileHash -LiteralPath (Join-Path $project $_)).Hash.ToLowerInvariant()}})}
# Runtime-generated verification result; excluded from its own recursive file hash inventory.
$result.newFiles=@($result.newFiles|Where-Object file -ne 'docs/assistant-team/integration/I2/I2-CHECKPOINT-VERIFICATION.json')
[IO.File]::WriteAllText((Join-Path $report 'I2-CHECKPOINT-VERIFICATION.json'),($result|ConvertTo-Json -Depth 8),$utf8)
[pscustomobject]@{ProtectedFiles=$protected.files.Count;Baseline=$base.files.Count;Copies=$copies;Frozen=$frozen;Regression=$keys.Count;ZeroWriteWindows=$windows.Count;Stopped=$true;SecretHits=$secretHits;UnexpectedFiles=$unexpected.Count}|ConvertTo-Json
