param([switch]$Build)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$local = Join-Path $root '.local'
$runtime = Get-Content (Join-Path $local 'runtime.json') -Raw | ConvertFrom-Json
$backend = Join-Path $root 'knowledge-graph\backend-java'
$frontend = Join-Path $root 'knowledge-graph\frontend'
$javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
if (-not $javaHome -or -not (Test-Path "$javaHome\bin\java.exe")) {
    $javaHome = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory | Where-Object Name -Like 'jdk-17*' | Select-Object -First 1).FullName
}
if (-not $javaHome) { throw 'JDK 17 is required.' }
$env:JAVA_HOME = $javaHome
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Users/Administrator/.knowledge-graph-dev/tmp'
New-Item -ItemType Directory -Force "$local\logs" | Out-Null

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); return $true } catch { return $false } finally { $client.Dispose() }
}
function Save-Process($Process, [string]$Name) {
    @{ Id = $Process.Id; Started = $Process.StartTime.ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content "$local\$Name.process.json"
}
function Wait-Port([int]$Port) {
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-Port $Port) { return }
        Start-Sleep -Seconds 1
    }
    throw "Port $Port did not become ready. See .local/logs."
}

if (-not (Test-Port 13306)) {
    $process = Start-Process -FilePath $runtime.mysqlExe -ArgumentList ('--defaults-file="' + $runtime.mysqlConfig + '"') -WindowStyle Hidden -PassThru
    Save-Process $process 'mysql'
    Wait-Port 13306
}
$jar = Join-Path $backend 'target\backend-java-0.1.0.jar'
if ($Build -or -not (Test-Path $jar)) {
    if (Test-Port 8080) { throw 'Stop the development services before rebuilding the backend.' }
    Push-Location $backend
    try {
        & .\mvnw.cmd package -B
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally { Pop-Location }
}
if (-not (Test-Port 8080)) {
    $process = Start-Process -FilePath "$javaHome\bin\java.exe" -ArgumentList ('-jar "' + $jar + '"') -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput "$local\logs\backend.log" -RedirectStandardError "$local\logs\backend-error.log" -PassThru
    Save-Process $process 'backend'
}
Wait-Port 8080
$health = Invoke-RestMethod 'http://127.0.0.1:8080/api/health'
if ($health.code -ne 0) { throw 'Backend health check failed.' }
if (-not (Test-Port 5173)) {
    $node = (Get-Command node.exe).Source
    $process = Start-Process -FilePath $node -ArgumentList 'node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort' -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput "$local\logs\frontend.log" -RedirectStandardError "$local\logs\frontend-error.log" -PassThru
    Save-Process $process 'frontend'
}
Wait-Port 5173
Write-Host 'Ready: http://127.0.0.1:5173'
