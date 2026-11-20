$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $ProjectRoot
$DataDir = Join-Path $ProjectRoot "data"
$KlineDir = Join-Path $DataDir "kline"

if (-not (Test-Path $KlineDir)) {
  throw "Missing bundled data source: $KlineDir"
}

Push-Location (Join-Path $ProjectRoot "frontend")
npm install
npm run build
Pop-Location

python -m pip install -r requirements-build.txt

python -m PyInstaller `
  --noconfirm `
  --clean `
  --onedir `
  --name KLineTrainer `
  --paths src `
  --add-data "src\kline_trainer\static;kline_trainer\static" `
  run.py

$DistDir = Join-Path $ProjectRoot "dist\KLineTrainer"
$DistDataDir = Join-Path $DistDir "data"
New-Item -ItemType Directory -Force -Path $DistDataDir | Out-Null
robocopy $DataDir $DistDataDir /E /MT:16 /R:2 /W:1 /NFL /NDL /NJH /NJS /NC /NS /NP
$RoboCopyExitCode = $LASTEXITCODE
if ($RoboCopyExitCode -gt 7) {
  throw "Failed to copy data directory to $DistDataDir. Robocopy exit code: $RoboCopyExitCode"
}

Write-Host ""
Write-Host "Build complete:"
Write-Host (Join-Path $ProjectRoot "dist\KLineTrainer\KLineTrainer.exe")
Write-Host "Data directory:"
Write-Host $DistDataDir
