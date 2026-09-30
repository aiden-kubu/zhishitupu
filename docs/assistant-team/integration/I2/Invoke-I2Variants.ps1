$ErrorActionPreference='Stop'
$fixture=Get-Content -LiteralPath (Join-Path $projectRoot 'docs/assistant-team/integration/I1/fixtures/I1-FIXTURES.json') -Raw|ConvertFrom-Json
function Literal([string]$value) { "CONVERT(X'"+[Convert]::ToHexString($utf8.GetBytes($value))+"' USING utf8mb4)" }
function Loader([string]$sql) {
    $password=New-Password; $secrets.Add($password)
    [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' IDENTIFIED BY '$password' ACCOUNT UNLOCK;")
    try { [void](Invoke-I2Sql 'kg_i2_loader' $password ("START TRANSACTION;`n"+$sql+"`nCOMMIT;") $db) }
    finally { [void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' ACCOUNT LOCK;") }
}
$baseChunk=@($fixture.chunks|Where-Object id -eq 7401)[0]
$nextChunk=@($fixture.chunks|Where-Object id -eq 7402)[0]
$baseUnit=$baseChunk.content+$nextChunk.content
$restore="UPDATE document_chunks SET content=$(Literal $baseChunk.content),content_hash='$($baseChunk.contentHash)',end_offset=$($baseChunk.endOffset) WHERE id=7401;`nUPDATE document_chunks SET start_offset=$($nextChunk.startOffset),end_offset=$($nextChunk.endOffset) WHERE id=7402;`nUPDATE document_units SET extracted_text=$(Literal $baseUnit) WHERE id=7301;`nUPDATE document_metadata SET lifecycle_status='active',updated_at='2026-09-27 00:00:00' WHERE document_id=7201;`nUPDATE learning_path_steps SET status='locked',updated_at='2026-09-27 00:00:00' WHERE id=8102;`nUPDATE knowledge_nodes SET status='active',updated_at='2026-09-27 00:00:00' WHERE id=7701;`nUPDATE learning_paths SET profile_key='local-default',updated_at='2026-09-27 00:00:00' WHERE id=1000;`nUPDATE entity_candidates SET review_status='APPROVED' WHERE id=7601;"
$long=$baseChunk.content*100
$cases=[ordered]@{
    HASH_CHANGED="UPDATE document_chunks SET content=CONCAT(content,'I2 changed'),content_hash=SHA2(content,256),end_offset=CHAR_LENGTH(content) WHERE id=7401; UPDATE document_chunks SET start_offset=start_offset+10,end_offset=end_offset+10 WHERE id=7402; UPDATE document_units SET extracted_text=CONCAT($(Literal ($baseChunk.content+'I2 changed')),$(Literal $nextChunk.content)) WHERE id=7301;"
    ARCHIVE_A="UPDATE document_metadata SET lifecycle_status='archived' WHERE document_id=7201;"
    LONG_A="UPDATE document_chunks SET content=$(Literal $long),content_hash=SHA2(content,256),end_offset=$($long.Length) WHERE id=7401; UPDATE document_chunks SET start_offset=$($long.Length),end_offset=$($long.Length+$nextChunk.content.Length) WHERE id=7402; UPDATE document_units SET extracted_text=$(Literal ($long+$nextChunk.content)) WHERE id=7301;"
    MULTI_CURRENT="UPDATE learning_path_steps SET status='current' WHERE id=8102;"
    INACTIVE_CURRENT="UPDATE knowledge_nodes SET status='inactive' WHERE id=7701;"
    NO_PATH="UPDATE learning_paths SET profile_key='i2-parked-not-current' WHERE id=1000;"
    ACCEPTED_STATE="UPDATE entity_candidates SET review_status='ACCEPTED' WHERE id=7601;"
}
$manifest=@()
foreach($case in $cases.Keys) {
    Write-Output "I2 controlled database variant: $case"
    try {
        Loader $cases[$case]
        & (Join-Path $reportRoot 'Invoke-I2BaseVerification.ps1') -Suite Variant -Case $case
        $manifest+=@{case=$case;status='passed';loaderMutationOutsideReadWindow=$true;readerCredentialOnly=$true}
    } finally { Loader $restore }
    [IO.File]::WriteAllText((Join-Path $reportRoot 'I2-VARIANTS.json'),($manifest|ConvertTo-Json -Depth 5),$utf8)
}
& (Join-Path $reportRoot 'Invoke-I2BaseVerification.ps1') -Suite Budget -Case BUDGET
