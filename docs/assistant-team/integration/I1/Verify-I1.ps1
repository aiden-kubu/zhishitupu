[CmdletBinding()]
param([string]$ProjectRoot = 'D:\知识图谱')
# Read-only verification. Does not start services, compile, connect to DB/model, or write files.
$ErrorActionPreference = 'Stop'
$stageRoot = Join-Path $ProjectRoot 'docs/assistant-team/integration/I1'
$utf8 = [Text.UTF8Encoding]::new($false)
$sha = [Security.Cryptography.SHA256]::Create()
function Read-Text([string]$Path) { [IO.File]::ReadAllText($Path, $utf8).Replace(([string][char]13 + [char]10), [string][char]10) }
function Text-Hash([string]$Content) { [Convert]::ToHexString($sha.ComputeHash($utf8.GetBytes($Content))).ToLowerInvariant() }
function File-Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
$manifest = Read-Text (Join-Path $stageRoot 'I1-BASELINE.json') | ConvertFrom-Json
$before = Read-Text (Join-Path $stageRoot 'I1-PROTECTION-BEFORE.json') | ConvertFrom-Json
$fixture = Read-Text (Join-Path $stageRoot 'fixtures/I1-FIXTURES.json') | ConvertFrom-Json
$cases = (Read-Text (Join-Path $stageRoot 'fixtures/I1-CASES.json') | ConvertFrom-Json).cases
$checked = 0
$copies = 0
foreach ($entry in $manifest.files) {
    $actual = Text-Hash (Read-Text (Join-Path $ProjectRoot $entry.file))
    Require ($actual -eq $entry.normalizedSha256) "Baseline mismatch: $($entry.file)"
    Require ((Text-Hash (Read-Text $entry.source)) -eq $entry.normalizedSha256) "Owner snapshot changed: $($entry.source)"
    foreach ($copy in $entry.identicalCopies) {
        Require ((Text-Hash (Read-Text $copy)) -eq $entry.normalizedSha256) "Duplicate dependency changed: $copy"
        $copies++
    }
    $checked++
}
Require ($checked -eq 62 -and $copies -eq 98) 'Unexpected source or duplicate count'
foreach ($entry in $before.files) {
    Require ((File-Hash (Join-Path $ProjectRoot $entry.file)) -eq $entry.rawSha256) "Pre-existing main file changed: $($entry.file)"
}
$head = (& git -C $ProjectRoot rev-parse HEAD).Trim()
Require ($LASTEXITCODE -eq 0 -and $head -eq $before.head) 'Main HEAD changed'
$branch = (& git -C $ProjectRoot branch --show-current).Trim()
Require ($LASTEXITCODE -eq 0 -and $branch -eq $before.branch) 'Main branch changed'
$allowed = @{}
foreach ($entry in $before.files) { $allowed[$entry.file] = $true }
foreach ($entry in $manifest.files) { $allowed[$entry.file] = $true }
$visible = @(& git -C $ProjectRoot -c core.quotepath=false ls-files --cached --others --exclude-standard)
Require ($LASTEXITCODE -eq 0) 'Cannot inspect main Git files'
$unexpected = @($visible | Where-Object { -not $allowed.ContainsKey($_) -and -not $_.StartsWith('docs/assistant-team/integration/I1/') })
Require ($unexpected.Count -eq 0) "Unexpected main files: $($unexpected -join ', ')"
$frozen = @()
foreach ($spec in $manifest.frozenManifests) {
    $f = Read-Text (Join-Path $ProjectRoot $spec.file) | ConvertFrom-Json
    Require ($f.files.Count -eq $spec.expectedEntries) "Frozen manifest count changed: $($spec.file)"
    foreach ($entry in $f.files) {
        Require ((Text-Hash (Read-Text (Join-Path $spec.targetRoot $entry.file))) -eq $entry.normalizedSha256) "Frozen dependency changed: $($entry.file)"
    }
    $frozen += [pscustomobject]@{file=$spec.file;matched=$f.files.Count}
}
$toolNames = @()
foreach ($entry in @($manifest.files | Where-Object { $_.file -like '*AgentToolProvider.java' })) {
    foreach ($m in [regex]::Matches((Read-Text (Join-Path $ProjectRoot $entry.file)), 'tool\("([^"]+)"')) { $toolNames += $m.Groups[1].Value }
}
Require ($toolNames.Count -eq 33 -and @($toolNames | Select-Object -Unique).Count -eq 33) 'Tool name conflict/count mismatch'
$suites = @()
$caseKeys = @{}
foreach ($entry in @($manifest.files | Where-Object kind -eq 'test')) {
    $fqcn = $entry.file.Replace('backend-java/src/test/java/','').Replace('/','.').Replace('.java','')
    $xmlPath = Join-Path $ProjectRoot "backend-java/target/surefire-reports/TEST-$fqcn.xml"
    [xml]$xml = [IO.File]::ReadAllText($xmlPath)
    $s = $xml.testsuite
    Require ($s.name -eq $fqcn) "Wrong test suite: $fqcn"
    Require ([int]$s.failures -eq 0 -and [int]$s.errors -eq 0 -and [int]$s.skipped -eq 0) "Test failure/error/skip: $fqcn"
    Require (@($s.testcase).Count -eq [int]$s.tests) "Test XML count mismatch: $fqcn"
    foreach ($tc in $s.testcase) {
        $key = [string]$tc.classname + '#' + [string]$tc.name
        Require (-not $caseKeys.ContainsKey($key)) "Duplicate executed test: $key"
        $caseKeys[$key] = $true
    }
    $suites += [pscustomobject]@{owner=$entry.owner;name=$fqcn;tests=[int]$s.tests;failures=[int]$s.failures;errors=[int]$s.errors;skipped=[int]$s.skipped;seconds=[double]$s.time;reportRawSha256=(File-Hash $xmlPath);lastWriteTime=(Get-Item -LiteralPath $xmlPath).LastWriteTime.ToString('o')}
}
Require ($suites.Count -eq 19 -and $caseKeys.Count -eq 154) 'Unexpected deduplicated test count'
$byDomain = @($suites | Group-Object owner | ForEach-Object { [pscustomobject]@{owner=$_.Name;classes=$_.Count;tests=($_.Group | Measure-Object -Property tests -Sum).Sum} })
function Id-Map($Items, [string]$Label) {
    $map = @{}
    foreach ($item in $Items) {
        $key = [string]$item.id
        Require ($item.id -gt 127 -and -not $map.ContainsKey($key)) "Bad/duplicate high ID in $Label"
        $map[$key] = $item
    }
    return $map
}
$libs = Id-Map $fixture.libraries 'libraries'
$docs = Id-Map $fixture.documents 'documents'
$units = Id-Map $fixture.units 'units'
$chunks = Id-Map $fixture.chunks 'chunks'
$jobs = Id-Map $fixture.jobs 'jobs'
$nodes = Id-Map $fixture.nodes 'nodes'
$edges = Id-Map $fixture.edges 'edges'
$paths = Id-Map $fixture.paths 'paths'
$steps = Id-Map $fixture.steps 'steps'
$attempts = Id-Map $fixture.legacyAttempts 'attempts'
$candidates = Id-Map $fixture.candidates 'candidates'
foreach ($doc in $fixture.documents) {
    Require ($libs.ContainsKey([string]$doc.libraryId)) 'Missing primary library'
    foreach ($lid in $doc.assignedLibraryIds) { Require ($libs.ContainsKey([string]$lid)) 'Missing assigned library' }
    if ($doc.contentFile) {
        $body = Read-Text (Join-Path $stageRoot ('fixtures/' + $doc.contentFile))
        $joined = (@($fixture.chunks | Where-Object documentId -eq $doc.id | Sort-Object chunkIndex | ForEach-Object content) -join '')
        Require ($body -ceq $joined) "Document/chunk body mismatch: $($doc.id)"
    }
}
foreach ($unit in $fixture.units) { Require ($docs.ContainsKey([string]$unit.documentId)) 'Missing unit document' }
foreach ($chunk in $fixture.chunks) {
    Require ($units.ContainsKey([string]$chunk.unitId)) 'Missing source unit'
    Require ($units[[string]$chunk.unitId].documentId -eq $chunk.documentId) 'Chunk ownership mismatch'
    Require ((Text-Hash $chunk.content) -eq $chunk.contentHash) "Chunk hash mismatch: $($chunk.id)"
    Require ($chunk.endOffset - $chunk.startOffset -eq $chunk.content.Length) 'Chunk offsets mismatch'
}
foreach ($unit in $fixture.units) {
    $offset = 0; $index = 0
    foreach ($chunk in @($fixture.chunks | Where-Object unitId -eq $unit.id | Sort-Object chunkIndex)) {
        Require ($chunk.chunkIndex -eq $index -and $chunk.startOffset -eq $offset) 'Non-contiguous source indices/offsets'
        $index++; $offset = $chunk.endOffset
    }
}
foreach ($job in $fixture.jobs) { Require ($docs.ContainsKey([string]$job.documentId)) 'Missing job document' }
foreach ($c in $fixture.candidates) {
    Require ($jobs.ContainsKey([string]$c.jobId)) 'Missing candidate job'
    foreach ($cid in $c.evidenceChunkIds) {
        Require ($chunks.ContainsKey([string]$cid) -and $chunks[[string]$cid].documentId -eq $jobs[[string]$c.jobId].documentId) 'Candidate source ownership mismatch'
    }
    if ($c.matchedNodeId) { Require ($nodes.ContainsKey([string]$c.matchedNodeId)) 'Missing candidate matched node' }
    if ($c.matchedEdgeId) { Require ($edges.ContainsKey([string]$c.matchedEdgeId)) 'Missing candidate matched edge' }
}
foreach ($n in $fixture.nodes) {
    foreach ($lid in $n.libraryIds) { Require ($libs.ContainsKey([string]$lid)) 'Missing node library' }
    foreach ($cid in $n.evidenceChunkIds) { Require ($chunks.ContainsKey([string]$cid)) 'Missing node source' }
}
foreach ($e in $fixture.edges) {
    Require ($nodes.ContainsKey([string]$e.sourceNodeId) -and $nodes.ContainsKey([string]$e.targetNodeId)) 'Missing edge endpoint'
    foreach ($cid in $e.evidenceChunkIds) { Require ($chunks.ContainsKey([string]$cid)) 'Missing edge source' }
}
Require ($fixture.paths.Count -eq 1 -and $fixture.paths[0].profileKey -eq 'local-default') 'Invalid base local-default cardinality'
foreach ($p in $fixture.paths) { Require ($libs.ContainsKey([string]$p.libraryId)) 'Missing path library' }
foreach ($s in $fixture.steps) {
    Require ($paths.ContainsKey([string]$s.pathId) -and $nodes.ContainsKey([string]$s.nodeId)) 'Missing step parent'
    Require ($nodes[[string]$s.nodeId].libraryIds -contains $paths[[string]$s.pathId].libraryId) 'Step node not in path library'
}
Require (@($fixture.steps | Where-Object status -eq 'current').Count -eq 1) 'Invalid base current stage'
foreach ($a in $fixture.legacyAttempts) {
    Require ($paths.ContainsKey([string]$a.pathId)) 'Missing attempt path'
    if ($null -ne $a.stepId) {
        Require ($steps.ContainsKey([string]$a.stepId) -and $steps[[string]$a.stepId].pathId -eq $a.pathId -and $a.actualStepPathId -eq $a.pathId) 'Bad base attempt ownership'
    }
}
$visibleNodes = @($fixture.nodes | Where-Object { $_.libraryIds -contains 7101 } | ForEach-Object id)
$visibleEdges = @($fixture.edges | Where-Object { $_.status -eq 'active' -and $visibleNodes -contains $_.sourceNodeId -and $visibleNodes -contains $_.targetNodeId })
$isolated = @($visibleNodes | Where-Object { $id = $_; @($visibleEdges | Where-Object { $_.sourceNodeId -eq $id -or $_.targetNodeId -eq $id }).Count -eq 0 })
Require ($visibleNodes.Count -eq 4 -and $visibleEdges.Count -eq 1 -and $isolated.Count -eq 2) 'Fixture insight expectation mismatch'
Require ($cases.Count -eq 36 -and @($cases.id | Select-Object -Unique).Count -eq 36) 'Acceptance case count/identity mismatch'
Require ($fixture.variants.Count -eq 12) 'Variant count mismatch'
foreach ($case in $cases) {
    Require ($case.action -and $case.expected -and $case.page -and $case.status -eq 'prepared_not_executed_as_this_catalog') 'Incomplete acceptance case'
    Require (@($fixture.scopes.key) -contains $case.scope) 'Unknown case scope'
}
$taskKeys = @()
foreach ($task in $fixture.handoff.tasks) {
    foreach ($dependency in $task.dependsOn) { Require ($taskKeys -contains $dependency) 'Unknown/forward handoff dependency' }
    Require ($taskKeys -notcontains $task.key) 'Duplicate handoff task'
    $taskKeys += $task.key
}
$original05 = Read-Text (Join-Path $stageRoot 'archive/05-assessment-W1.before-cleanup.md')
$clean05 = Read-Text (Join-Path $stageRoot 'reports/05-assessment-W1.md')
$source05 = 'D:/知识图谱-worktrees/assistant-05-assessment/docs/assistant-team/reports/05-assessment-W1.md'
Require ((Read-Text $source05) -ceq $clean05) '05 clean archive differs from original report'
$start = $original05.IndexOf('backend-') + 'backend-'.Length
$tail = $original05.IndexOf('java/src/main/java/com/knowledgegraph/assistant/assessment/', $start)
Require ($start -ge 8 -and $tail -gt $start) '05 archive cleanup markers missing'
Require ($original05.Remove($start, $tail-$start) -ceq $clean05) '05 cleanup changed technical report content'
$reportArchives = @()
foreach ($name in @('01-ingestion','02-quality','03-research','04-learning')) {
    $source = "D:/知识图谱-worktrees/assistant-$name/docs/assistant-team/reports/$name-W1.md"
    $archive = Join-Path $stageRoot "reports/$name-W1.md"
    Require ((Read-Text $source) -ceq (Read-Text $archive)) "Report archive mismatch: $name"
    $reportArchives += [pscustomobject]@{name=$name;source=$source;normalizedSha256=(Text-Hash (Read-Text $archive))}
}
[pscustomobject]@{
    stage='I1'; verifiedAt=(Get-Date).ToString('o'); kind='offline_read_only_verification_not_user_acceptance';
    head=$head; branch=$branch; baselineFilesMatched=$checked; identicalDuplicateCopiesMatched=$copies;
    preExistingMainFilesUnchanged=$before.files.Count; unexpectedNewMainFiles=$unexpected; frozen=$frozen;
    productionRegistrationPerformedInI1=$false; toolNamesStaticallyUnique=$toolNames.Count;
    toolCheckNote='Static declarations only; no production registration or all-domain production host exercised';
    testClasses=$suites.Count; deduplicatedTests=$caseKeys.Count; failures=0; errors=0; skipped=0;
    testsByDomain=$byDomain; suites=$suites;
    fixture=[pscustomobject]@{documents=$fixture.documents.Count;chunks=$fixture.chunks.Count;chunkHashesMatched=$fixture.chunks.Count;nodes=$fixture.nodes.Count;edges=$fixture.edges.Count;paths=$fixture.paths.Count;steps=$fixture.steps.Count;legacyAttempts=$fixture.legacyAttempts.Count;faultVariants=$fixture.variants.Count;acceptanceCases=$cases.Count;logicalVisibleNodes=$visibleNodes.Count;logicalActiveEdges=$visibleEdges.Count;logicalIsolatedNodes=$isolated.Count;databaseLoaded=$false;websiteExecuted=$false};
    report05=[pscustomobject]@{removedConversationCharacters=$tail-$start;originalNormalizedSha256=(Text-Hash $original05);cleanNormalizedSha256=(Text-Hash $clean05);technicalContentUnchanged=$true};
    reportArchives=$reportArchives
} | ConvertTo-Json -Depth 10
