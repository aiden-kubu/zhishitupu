# Generates I2-only SQL and derived file artifacts from the frozen I1 input.
# Does not connect to any database. Called only by the guarded I2 orchestrator.
$ErrorActionPreference = 'Stop'
$fixturePath = 'D:\知识图谱\docs\assistant-team\integration\I1\fixtures\I1-FIXTURES.json'
$fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
$fixtureDir = Split-Path $fixturePath
$derivedDir = 'D:\知识图谱-i2-env\fixture-files'
[void](New-Item -ItemType Directory -Path $derivedDir -Force)
$sql = [Collections.Generic.List[string]]::new()
function Value($v) {
    if ($null -eq $v) { return 'NULL' }
    if ($v -is [bool]) { return [string][int]$v }
    if ($v -is [ValueType] -and $v -isnot [datetime]) { return [string]$v }
    return "CONVERT(X'" + [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes([string]$v)) + "' USING utf8mb4)"
}
function Row([string]$table, [System.Collections.IDictionary]$fields) {
    $sql.Add("INSERT INTO $table (" + ($fields.Keys -join ',') + ') VALUES (' + (($fields.Values | ForEach-Object { Value $_ }) -join ',') + ');')
}
function Hash([byte[]]$bytes) { [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant() }
$stamp = '2026-09-27 00:00:00'
$sql.Add('CREATE TABLE i2_privilege_canary (id BIGINT PRIMARY KEY, value_text VARCHAR(20)) ENGINE=InnoDB;')
$sql.Add('START TRANSACTION;')
# Exact V3 demo IDs in this newly created disposable database only; no broad deletion.
$sql.Add('DELETE FROM knowledge_edges WHERE id=1 AND source_node_id=1 AND target_node_id=2;')
$sql.Add('DELETE FROM library_nodes WHERE library_id=1 AND node_id IN (1,2);')
$sql.Add("DELETE FROM knowledge_nodes WHERE (id=1 AND canonical_name='TCP') OR (id=2 AND canonical_name='UDP');")
$sql.Add("DELETE FROM knowledge_libraries WHERE id=1 AND type='course';")
Row 'i2_privilege_canary' ([ordered]@{id=1;value_text='untouched'})
foreach ($l in $fixture.libraries) { Row 'knowledge_libraries' ([ordered]@{id=$l.id;name=$l.name;type='topic';status=$l.status;created_at=$stamp;updated_at=$stamp}) }
Row 'library_aliases' ([ordered]@{id=72001;library_id=7101;alias='I2 acceptance';normalized_alias='i2 acceptance';created_at=$stamp})
$files = @()
foreach ($d in $fixture.documents) {
    $content = ''
    if ($d.contentFile) { $path = Join-Path $fixtureDir $d.contentFile; $bytes = [IO.File]::ReadAllBytes($path) }
    elseif ($d.filePresent) {
        $body = ($fixture.chunks | Where-Object documentId -eq $d.id | Sort-Object chunkIndex | ForEach-Object content) -join ''
        # Distinct whole-file content, but the unit/chunk text remains the frozen I1 text.
        $bytes = [Text.Encoding]::UTF8.GetBytes($body + "`n[I2 synthetic document $($d.id) footer]`n")
        $path = Join-Path $derivedDir $d.name
        if (Test-Path -LiteralPath $path) { if ((Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne (Hash $bytes)) { throw 'Derived fixture already exists with unexpected content' } }
        else { [IO.File]::WriteAllBytes($path,$bytes) }
    } else {
        $path = Join-Path $derivedDir $d.name
        if (Test-Path -LiteralPath $path) { throw 'Missing-file fixture unexpectedly exists' }
        # Deterministic upload metadata for an intentionally absent original file.
        $bytes = [Text.Encoding]::UTF8.GetBytes("I2 missing upload $($d.id)")
    }
    $sha = Hash $bytes
    Row 'documents' ([ordered]@{id=$d.id;library_id=$d.libraryId;original_name=$d.name;stored_name=$d.name;mime_type='text/plain';extension='txt';size_bytes=$bytes.Length;sha256=$sha;storage_path=$path.Replace('\','/');status=$d.status;created_at=$stamp;updated_at=$stamp})
    Row 'document_metadata' ([ordered]@{document_id=$d.id;title=$d.name;lifecycle_status=$d.lifecycleStatus;created_at=$stamp;updated_at=$stamp})
    foreach ($library in $d.assignedLibraryIds) { Row 'document_topic_assignments' ([ordered]@{document_id=$d.id;temp_key='i2-assignment';library_id=$library}) }
    $files += @{documentId=$d.id;path=$path;filePresent=[bool]$d.filePresent;fileSha256=$sha;fileBytes=$bytes.Length}
}
Row 'document_tags' ([ordered]@{id=72101;document_id=7201;tag='合成验收';normalized_tag='合成验收';source='human';created_at=$stamp})
foreach ($u in $fixture.units) {
    $body = ($fixture.chunks | Where-Object unitId -eq $u.id | Sort-Object chunkIndex | ForEach-Object content) -join ''
    Row 'document_units' ([ordered]@{id=$u.id;document_id=$u.documentId;unit_index=$u.unitIndex;unit_type=$u.unitType;source_locator=$u.locator;status=$u.status;extracted_text=$body;created_at=$stamp})
}
foreach ($c in $fixture.chunks) {
    if ((Hash ([Text.Encoding]::UTF8.GetBytes($c.content))) -ne $c.contentHash) { throw "Frozen chunk hash mismatch: $($c.id)" }
    Row 'document_chunks' ([ordered]@{id=$c.id;unit_id=$c.unitId;chunk_index=$c.chunkIndex;content=$c.content;content_hash=$c.contentHash;start_offset=$c.startOffset;end_offset=$c.endOffset;created_at=$stamp})
}
foreach ($j in $fixture.jobs) {
    Row 'ingestion_jobs' ([ordered]@{id=$j.id;document_id=$j.documentId;stage=$j.stage;status=$j.status;progress=$(if ($j.status -eq 'COMPLETED') {100} else {0});processed_units=$(if ($j.status -eq 'COMPLETED') {1} else {0});total_units=1;retry_count=$j.retryCount;error_code=$j.errorCode;created_at=$stamp;finished_at=$stamp})
}
foreach ($n in $fixture.nodes) {
    Row 'knowledge_nodes' ([ordered]@{id=$n.id;canonical_name=$n.name;node_type=$n.type;definition='I2 synthetic fact';status=$n.status;created_at=$stamp;updated_at=$stamp})
    foreach ($l in $n.libraryIds) { Row 'library_nodes' ([ordered]@{library_id=$l;node_id=$n.id}) }
    foreach ($c in $n.evidenceChunkIds) { Row 'node_evidence' ([ordered]@{node_id=$n.id;chunk_id=$c;created_at=$stamp}) }
}
Row 'node_aliases' ([ordered]@{id=77001;node_id=7701;alias='Alpha';normalized_alias='alpha'})
foreach ($e in $fixture.edges) {
    Row 'knowledge_edges' ([ordered]@{id=$e.id;source_node_id=$e.sourceNodeId;target_node_id=$e.targetNodeId;relation_type=$e.relationType;status=$e.status;created_at=$stamp;updated_at=$stamp})
    foreach ($c in $e.evidenceChunkIds) { Row 'edge_evidence' ([ordered]@{edge_id=$e.id;chunk_id=$c;created_at=$stamp}) }
}
foreach ($c in $fixture.candidates) {
    $edited = if ($c.aiDecision) { @{aiReview=$c.aiDecision} | ConvertTo-Json -Depth 5 -Compress } else { $null }
    $fields = [ordered]@{id=$c.id;job_id=$c.jobId;review_status=$c.reviewStatus;evidence_chunk_ids_json=(ConvertTo-Json -InputObject @($c.evidenceChunkIds) -Compress);edited_payload_json=$edited;created_at=$stamp}
    if ($c.kind -eq 'entity') { $fields.name=$c.name; $fields.temp_key=$c.tempKey; $fields.matched_node_id=$c.matchedNodeId; Row 'entity_candidates' $fields }
    else { $fields.source_temp_key=$c.sourceTempKey; $fields.target_temp_key=$c.targetTempKey; $fields.relation_type=$c.relationType; $fields.matched_edge_id=$c.matchedEdgeId; Row 'relation_candidates' $fields }
}
foreach ($p in $fixture.paths) { Row 'learning_paths' ([ordered]@{id=$p.id;profile_key=$p.profileKey;library_id=$p.libraryId;goal=$p.goal;difficulty=$p.difficulty;status=$p.status;created_at=$stamp;updated_at=$stamp}) }
foreach ($s in $fixture.steps) {
    Row 'learning_path_steps' ([ordered]@{id=$s.id;path_id=$s.pathId;step_key=$s.stepKey;node_id=$s.nodeId;position_index=$s.position;title=$s.title;description_text='I2 synthetic description';detail_text='I2 synthetic detail';estimated_minutes=$s.estimatedMinutes;status=$s.status;visited_at=$(if($s.visitedAt){$stamp}else{$null});assessment_score=$s.legacyAssessmentScore;created_at=$stamp;updated_at=$stamp})
}
foreach ($a in $fixture.legacyAttempts) { Row 'learning_assessment_attempts' ([ordered]@{id=$a.id;path_id=$a.pathId;step_id=$a.stepId;score=$a.score;correct_count=$a.correctCount;total_count=$a.totalCount;weak_topics=$a.weakTopicsJson;created_at=$(if($a.id -eq 9101){'2026-09-26 00:00:00'}else{$stamp})}) }
Row 'system_settings' ([ordered]@{setting_key='storage.maxUploadSizeMb';encrypted_value='123';is_secret=0;updated_at=$stamp})
$sql.Add('COMMIT;')
$mapping = @{stage='I2';kind='fixture_mapping_not_execution_result';sourceFile=$fixturePath;sourceRawSha256=(Get-FileHash -LiteralPath $fixturePath).Hash.ToLowerInvariant();files=$files;fixedTimestampUtc=$stamp;derivedExpectations=@('7501.reviewable=true from existing entity candidates, not I1 logical false','7502.extractable=true from completed chunks and zero candidates, not I1 logical false','APPROVED preserved as nonstandard raw legacy state; never silently converted to ACCEPTED','actualStepPathId derived by LEFT JOIN, not a stored column','TEXT is synthetic unit metadata, not proof of real document parser','Missing files have deterministic historical upload metadata, not verified on-disk hashes');foreignKeysDisabled=$false}
[IO.File]::WriteAllText('D:\知识图谱\docs\assistant-team\integration\I2\I2-FIXTURE-MAPPING.json',($mapping | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
return ($sql -join "`n")
