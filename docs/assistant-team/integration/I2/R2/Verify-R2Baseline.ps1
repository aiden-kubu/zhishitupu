param([switch]$RequirePackage)
$ErrorActionPreference='Stop'
$root='D:\知识图谱'
$r2=Join-Path $root 'docs/assistant-team/integration/I2/R2'
function Norm([string]$Path){
    $text=[IO.File]::ReadAllText($Path,[Text.Encoding]::UTF8).Replace("`r`n","`n")
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text))).ToLowerInvariant()
}
function Require([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
$before=Get-Content -LiteralPath (Join-Path $r2 'R2-PROTECTION-BEFORE.json') -Raw|ConvertFrom-Json
$overlay=Get-Content -LiteralPath (Join-Path $r2 'R2-OVERLAY.json') -Raw|ConvertFrom-Json
Require ($overlay.changes.Count -eq 2 -and $overlay.additions.Count -eq 1) 'R2 approved change count mismatch'
foreach($entry in $before.files){
    $change=$overlay.changes|Where-Object file -eq $entry.file
    $expected=if($change){$change.newRawSha256}else{$entry.rawSha256}
    Require ((Get-FileHash -LiteralPath (Join-Path $root $entry.file)).Hash.ToLowerInvariant() -eq $expected) "Protected content mismatch: $($entry.file)"
}
foreach($change in $overlay.changes){
    Require ((Get-FileHash -LiteralPath (Join-Path $r2 $change.archive)).Hash.ToLowerInvariant() -eq $change.oldRawSha256) 'R1 preimage archive mismatch'
    Require ((Norm $change.owner) -eq $change.newNormalizedSha256) 'Owner accepted source changed'
    Require ((Norm (Join-Path $root $change.file)) -eq $change.newNormalizedSha256) 'Received R2 source mismatch'
}
foreach($addition in $overlay.additions){
    Require ((Norm $addition.owner) -eq $addition.normalizedSha256) 'Owner accepted test changed'
    Require ((Norm (Join-Path $root $addition.file)) -eq $addition.normalizedSha256) 'Received R2 test mismatch'
}
Require ((Get-FileHash -LiteralPath (Join-Path $r2 'archive/02-quality-I2-R2.md')).Hash.ToLowerInvariant() -eq $overlay.reportRawSha256) 'R2 report archive mismatch'
$base=Get-Content -LiteralPath (Join-Path $root 'docs/assistant-team/integration/I1/I1-BASELINE.json') -Raw|ConvertFrom-Json
$copies=0;$frozen=0
foreach($entry in $base.files){
    $change=$overlay.changes|Where-Object file -eq $entry.file
    $expected=if($change){$change.newNormalizedSha256}else{$entry.normalizedSha256}
    Require ((Norm (Join-Path $root $entry.file)) -eq $expected) "I1 with overlays mismatch: $($entry.file)"
    Require ((Norm $entry.source) -eq $expected) "Owner baseline mismatch: $($entry.source)"
    foreach($copy in $entry.identicalCopies){Require ((Norm $copy) -eq $entry.normalizedSha256) "Duplicate mismatch: $copy";$copies++}
}
foreach($spec in $base.frozenManifests){
    $manifest=Get-Content -LiteralPath (Join-Path $root $spec.file) -Raw|ConvertFrom-Json
    Require ($manifest.files.Count -eq $spec.expectedEntries) 'Frozen manifest count mismatch'
    foreach($entry in $manifest.files){Require ((Norm (Join-Path $spec.targetRoot $entry.file)) -eq $entry.normalizedSha256) "Frozen dependency mismatch: $($entry.file)";$frozen++}
}
$r1package=Get-Content -LiteralPath (Join-Path (Split-Path $r2) 'R1/R1-PACKAGE.json') -Raw|ConvertFrom-Json
foreach($entry in $r1package.files){
    $change=$overlay.changes|Where-Object file -eq $entry.file
    $expected=if($change){$change.newRawSha256}else{$entry.rawSha256}
    Require ((Get-FileHash -LiteralPath (Join-Path $root $entry.file)).Hash.ToLowerInvariant() -eq $expected) "Historical package with explicit R2 overlay mismatch: $($entry.file)"
}
foreach($entry in $r1package.environmentFiles){Require ((Get-FileHash -LiteralPath $entry.file).Hash.ToLowerInvariant() -eq $entry.rawSha256) 'Original environment changed'}
$allowed=@('R2-PROTECTION-BEFORE.json','R2-OVERLAY.json','R2-PACKAGE.json','R2-PLAN.md','R2-REPORT.md','Verify-R2Baseline.ps1','Invoke-R2Tests.ps1','Invoke-R2Verification.ps1','archive/JdbcQualityReadRepository.java.before.txt','archive/JdbcQualityInsightRankingRegressionTest.java.before.txt','archive/02-quality-I2-R2.md')
$extra=@(& git -C $root -c core.quotepath=false ls-files --cached --others --exclude-standard |Where-Object{$_ -notin $before.files.file})
foreach($file in $extra){
    $relative=$file.Replace('docs/assistant-team/integration/I2/R2/','')
    Require ($file -in $overlay.additions.file -or $file -eq 'backend-java/src/test/java/com/knowledgegraph/assistant/i2/I2QualityIsolatedCountTest.java' -or ($file.StartsWith('docs/assistant-team/integration/I2/R2/') -and ($relative -in $allowed -or $relative.StartsWith('evidence/')))) "Unexpected new path: $file"
}
Require ((& git -C $root rev-parse HEAD).Trim() -eq $before.head) 'HEAD changed'
Require ((& git -C $root branch --show-current).Trim() -eq $before.branch) 'Branch changed'
if($RequirePackage){
    $package=Get-Content -LiteralPath (Join-Path $r2 'R2-PACKAGE.json') -Raw|ConvertFrom-Json
    foreach($entry in $package.files){Require ((Get-FileHash -LiteralPath (Join-Path $root $entry.file)).Hash.ToLowerInvariant() -eq $entry.rawSha256) "R2 package changed: $($entry.file)"}
}
[pscustomobject]@{checkedAt=(Get-Date).ToString('o');priorFiles=$before.files.Count;approvedExistingChanges=2;otherPriorFilesUnchanged=$before.files.Count-2;baselineWithOverlays=$base.files.Count;duplicateCopies=$copies;frozenEntries=$frozen;packageChecked=[bool]$RequirePackage;extraFiles=$extra}
