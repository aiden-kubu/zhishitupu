param([switch]$RequirePackage)
$ErrorActionPreference='Stop'
$root='D:\知识图谱'
$r1=Join-Path $root 'docs/assistant-team/integration/I2/R1'
function NormalizedHash([string]$Path) {
    $text=[IO.File]::ReadAllText($Path,[Text.Encoding]::UTF8).Replace("`r`n","`n")
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text))).ToLowerInvariant()
}
function Require([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
$protection=Get-Content -LiteralPath (Join-Path $r1 'R1-PROTECTION-BEFORE.json') -Raw|ConvertFrom-Json
$overlay=Get-Content -LiteralPath (Join-Path $r1 'R1-OVERLAY.json') -Raw|ConvertFrom-Json
Require ($overlay.changes.Count -eq 1) 'Exactly one existing implementation change is approved'
$change=$overlay.changes[0]
foreach($entry in $protection.files){
    $expected=if($entry.file -eq $change.file){$change.newRawSha256}else{$entry.rawSha256}
    Require ((Get-FileHash -LiteralPath (Join-Path $root $entry.file)).Hash.ToLowerInvariant() -eq $expected) "Protected content mismatch: $($entry.file)"
}
Require ((Get-FileHash -LiteralPath (Join-Path $r1 $change.archive)).Hash.ToLowerInvariant() -eq $change.oldRawSha256) 'Original implementation archive mismatch'
Require ((NormalizedHash (Join-Path $root $change.file)) -eq $change.newNormalizedSha256) 'Integrated implementation hash mismatch'
Require ((NormalizedHash $change.owner) -eq $change.newNormalizedSha256) 'Accepted owner implementation changed'
Require ((NormalizedHash (Join-Path $root $overlay.additions[0].file)) -eq $overlay.additions[0].normalizedSha256) 'Accepted ranking test changed'
$base=Get-Content -LiteralPath (Join-Path $root 'docs/assistant-team/integration/I1/I1-BASELINE.json') -Raw|ConvertFrom-Json
$copies=0;$frozen=0
foreach($entry in $base.files){
    $expected=if($entry.file -eq $change.file){$change.newNormalizedSha256}else{$entry.normalizedSha256}
    Require ((NormalizedHash (Join-Path $root $entry.file)) -eq $expected) "I1 plus overlay mismatch: $($entry.file)"
    Require ((NormalizedHash $entry.source) -eq $expected) "Owner mismatch: $($entry.source)"
    foreach($copy in $entry.identicalCopies){Require ((NormalizedHash $copy) -eq $entry.normalizedSha256) "Duplicate mismatch: $copy";$copies++}
}
foreach($spec in $base.frozenManifests){
    $manifest=Get-Content -LiteralPath (Join-Path $root $spec.file) -Raw|ConvertFrom-Json
    Require ($manifest.files.Count -eq $spec.expectedEntries) 'Frozen entry count mismatch'
    foreach($entry in $manifest.files){Require ((NormalizedHash (Join-Path $spec.targetRoot $entry.file)) -eq $entry.normalizedSha256) "Frozen dependency mismatch: $($entry.file)";$frozen++}
}
$r1Files=@('R1-PROTECTION-BEFORE.json','R1-OVERLAY.json','R1-PACKAGE.json','R1-PLAN.md','Verify-R1Baseline.ps1','Invoke-R1Tests.ps1','Invoke-R1Verification.ps1','R1-FIXTURE.sql','R1-REPORT.md','archive/JdbcQualityReadRepository.java.before.txt','archive/02-quality-I2-R1.md')
$extra=@(& git -C $root -c core.quotepath=false ls-files --cached --others --exclude-standard | Where-Object { $_ -notin $protection.files.file })
foreach($file in $extra){
    $relative=$file.Replace('docs/assistant-team/integration/I2/R1/','')
    Require ($file -in $overlay.additions.file -or ($file.StartsWith('docs/assistant-team/integration/I2/R1/') -and ($relative -in $r1Files -or $relative.StartsWith('evidence/')))) "Unexpected new path: $file"
}
Require ((& git -C $root rev-parse HEAD).Trim() -eq $protection.head) 'HEAD changed'
Require ((& git -C $root branch --show-current).Trim() -eq $protection.branch) 'Branch changed'
if($RequirePackage){
    $package=Get-Content -LiteralPath (Join-Path $r1 'R1-PACKAGE.json') -Raw|ConvertFrom-Json
    foreach($entry in $package.files){Require ((Get-FileHash -LiteralPath (Join-Path $root $entry.file)).Hash.ToLowerInvariant() -eq $entry.rawSha256) "R1 executable/fixture package changed: $($entry.file)"}
    foreach($entry in $package.environmentFiles){Require ((Get-FileHash -LiteralPath $entry.file).Hash.ToLowerInvariant() -eq $entry.rawSha256) 'Original isolated environment changed'}
}
[pscustomobject]@{checkedAt=(Get-Date).ToString('o');priorFiles=$protection.files.Count;approvedExistingChanges=1;otherPriorFilesUnchanged=$protection.files.Count-1;baselineWithOverlay=$base.files.Count;duplicateCopies=$copies;frozenEntries=$frozen;packageChecked=[bool]$RequirePackage;extraFiles=$extra}
