param(
  [int]$BackendPort = 8080,
  [int]$FrontendPort = 5174,
  # 可选：启用双端口开发模式（前端 5174 + 后端 8080）。
  # 默认不加该参数即为“单端口验收模式”：先 build 前端到后端 static，然后只启动 8080，
  # 与 GitHub OAuth 的 Homepage/Callback（localhost:8080）完全一致。
  [switch]$DevDualPort,
  [switch]$NoKillPorts,
  [switch]$NoOpenBrowser
)

$ErrorActionPreference = 'Stop'

function Write-Step([string]$msg) {
  Write-Host ""
  Write-Host "==> $msg" -ForegroundColor Cyan
}

function Kill-Port([int]$port) {
  $lines = netstat -ano | findstr ":$port" | findstr "LISTENING"
  if (-not $lines) { return }

  foreach ($line in ($lines -split "`r?`n")) {
    if (-not $line) { continue }
    $parts = ($line -split '\s+') | Where-Object { $_ -and $_.Trim().Length -gt 0 }
    $procId = $parts[-1]
    if ($procId -match '^\d+$') {
      try {
        Write-Host "Killing PID $procId on port $port"
        taskkill /PID $procId /F | Out-Null
      } catch {
        Write-Host "Failed to kill PID $procId on port ${port}: $($_.Exception.Message)" -ForegroundColor Yellow
      }
    }
  }
}

function Import-LocalSecrets([string]$path) {
  if (Test-Path $path) {
    . $path
    return $true
  }
  return $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'dev-platform-backend'
$frontendDir = Join-Path $repoRoot 'front\front-spa'
$secretsPath = Join-Path $PSScriptRoot 'dev-secrets.ps1'

Write-Step "Loading local secrets (optional): $secretsPath"
$loaded = Import-LocalSecrets $secretsPath
if (-not $loaded) {
  Write-Host "No secrets file found. You can create it at:" -ForegroundColor Yellow
  Write-Host "  $secretsPath" -ForegroundColor Yellow
  Write-Host ""
  Write-Host "Example content (do NOT commit):" -ForegroundColor Yellow
  Write-Host '  $env:AI_ENABLED="true"' -ForegroundColor Yellow
  Write-Host '  $env:AI_API_KEY="sk-xxxx..."' -ForegroundColor Yellow
  Write-Host '  $env:AI_BASE_URL="https://api.siliconflow.cn/v1"' -ForegroundColor Yellow
  Write-Host '  $env:AI_MODEL="Qwen/Qwen2.5-7B-Instruct"' -ForegroundColor Yellow
}

# Defaults for convenience (can be overridden by dev-secrets.ps1)
if (-not $env:AI_ENABLED) { $env:AI_ENABLED = "true" }
if (-not $env:AI_BASE_URL) { $env:AI_BASE_URL = "https://api.siliconflow.cn/v1" }
if (-not $env:AI_MODEL) { $env:AI_MODEL = "Qwen/Qwen2.5-7B-Instruct" }

Write-Step "Stopping ports (Backend:$BackendPort, Frontend:$FrontendPort)"
if (-not $NoKillPorts) {
  Kill-Port $BackendPort
  # 默认模式不启用前端 dev server，但仍清理 5174，避免旧进程干扰
  Kill-Port $FrontendPort
} else {
  Write-Host "Skip killing ports due to -NoKillPorts"
}

Write-Step "Building frontend (output -> backend static)"
if (-not (Test-Path $frontendDir)) { throw "Frontend dir not found: $frontendDir" }
& powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location `"$frontendDir`"; npm run build"
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed (npm run build)" }

Write-Step "Starting backend"
if (-not (Test-Path $backendDir)) { throw "Backend dir not found: $backendDir" }
Start-Process -FilePath "powershell" -ArgumentList @(
  "-NoProfile",
  "-ExecutionPolicy", "Bypass",
  "-Command",
  "Set-Location `"$backendDir`"; `$env:SERVER_PORT=`"$BackendPort`"; .\mvnw.cmd -DskipTests spring-boot:run"
) | Out-Null

if ($DevDualPort) {
  Write-Step "Starting frontend"
  if (-not (Test-Path $frontendDir)) { throw "Frontend dir not found: $frontendDir" }
  Start-Process -FilePath "powershell" -ArgumentList @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-Command",
    "Set-Location `"$frontendDir`"; node .\\node_modules\\vite\\bin\\vite.js --host 127.0.0.1 --port $FrontendPort --strictPort"
  ) | Out-Null
}

Write-Step "Done"
if ($DevDualPort) {
  Write-Host "Frontend: http://127.0.0.1:$FrontendPort/"
}
Write-Host "App (OAuth homepage): http://localhost:$BackendPort/"
Write-Host "Backend:             http://127.0.0.1:$BackendPort/"

if (-not $NoOpenBrowser) {
  try {
    if ($DevDualPort) { Start-Process "http://127.0.0.1:$FrontendPort/" }
    else { Start-Process "http://localhost:$BackendPort/" }
  } catch {}
}

