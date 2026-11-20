# 把仓库根 data/kline/*.zip 同步到 Android assets，供 App 首次启动导入。
#
# Android 版把行情数据打包进 APK（app/src/main/assets/kline），PC 版直接读磁盘。
# 为了只维护一份数据，仓库根 data/ 是唯一数据源，本脚本负责镜像到 assets/。
#
# 用法（在仓库根或 android/ 下执行均可）：
#   pwsh -File android/scripts/sync_kline_assets.ps1
#   pwsh -File android/scripts/sync_kline_assets.ps1 -Clean   # 先清空 assets 再同步

param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$AndroidRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$RepoRoot = Resolve-Path (Join-Path $AndroidRoot "..")
$SourceDir = Join-Path $RepoRoot "data\kline"
$TargetDir = Join-Path $AndroidRoot "app\src\main\assets\kline"

if (-not (Test-Path $SourceDir)) {
    throw "找不到行情数据目录: $SourceDir`n请先把 data/kline/*.zip 放到仓库根 data/ 下。"
}

$zipCount = @(Get-ChildItem $SourceDir -Filter *.zip -File).Count
if ($zipCount -eq 0) {
    throw "行情数据目录里没有 zip: $SourceDir"
}

if ($Clean -and (Test-Path $TargetDir)) {
    Write-Host "清理 $TargetDir ..."
    Get-ChildItem $TargetDir -File | Where-Object { $_.Name -ne ".gitkeep" } | Remove-Item -Force
}

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

Write-Host "同步 $zipCount 个 zip: $SourceDir -> $TargetDir"
robocopy $SourceDir $TargetDir *.zip /MT:16 /R:2 /W:1 /NFL /NDL /NJH /NJS /NC /NS /NP | Out-Null
$exit = $LASTEXITCODE
if ($exit -gt 7) {
    throw "robocopy 失败，退出码: $exit"
}

$targetCount = @(Get-ChildItem $TargetDir -Filter *.zip -File).Count
Write-Host ""
Write-Host "完成：assets/kline 现有 $targetCount 个 zip"
if ($targetCount -ne $zipCount) {
    Write-Warning "数量不一致（源 $zipCount / 目标 $targetCount），请检查。"
}
