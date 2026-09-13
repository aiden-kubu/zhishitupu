$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '.local'
foreach ($name in @('frontend', 'backend')) {
    $recordPath = Join-Path $local "$name.process.json"
    if (-not (Test-Path $recordPath)) { continue }
    $record = Get-Content $recordPath -Raw | ConvertFrom-Json
    $process = Get-Process -Id $record.Id -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().ToString('o') -eq $record.Started) {
        Stop-Process -Id $process.Id
    }
}
$runtime = Get-Content (Join-Path $local 'runtime.json') -Raw | ConvertFrom-Json
& $runtime.mysqlAdmin "--defaults-extra-file=$($runtime.mysqlAdminConfig)" shutdown
if ($LASTEXITCODE -ne 0) { throw 'Database shutdown failed; it may already be stopped.' }
Write-Host 'Development services stopped. Database files retained.'
