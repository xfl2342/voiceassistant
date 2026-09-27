<#
.SYNOPSIS
    把手机上记的「改进意见」拉到电脑上。

.DESCRIPTION
    App 里每改一次意见，都会把全部内容重新写一份到手机的下载目录：

        内部存储 → Download → 生活助理-改进意见.md

    这个脚本用 adb 把那份文件取回工程目录（默认：工程根目录的 改进意见.md，
    已在 .gitignore 里，不会被提交），取回后把内容打印出来，方便直接看、直接改。

    前提：手机用数据线连上电脑，并且已开启「开发者选项 → USB 调试」。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\pull-feedback.ps1

.EXAMPLE
    # 指定输出位置
    powershell -ExecutionPolicy Bypass -File tools\pull-feedback.ps1 -OutFile D:\临时\意见.md
#>
param(
    [string]$OutFile = "",
    [string]$Adb = ""
)

# 故意用 Continue：adb 把进度和「找不到文件」一类的消息都写在 stderr 上，
# Windows PowerShell 5.1 在 Stop 模式下会把这当成致命错误，脚本还没走到判断就退出了。
$ErrorActionPreference = "Continue"

# 提示信息里有中文。Windows PowerShell 5.1 的控制台默认按本机代码页解码，
# 不设成 UTF-8 的话打印出来会是乱码。脚本本身要存成「UTF-8 带 BOM」，
# 否则 5.1 会把中文注释按 GBK 解析，直接报语法错误。
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

# 目标文件名与 App 里的 FeedbackExporter.FILE_NAME 保持一致，改一处要改两处。
$RemoteName = "生活助理-改进意见.md"
$RemotePath = "/sdcard/Download/$RemoteName"

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutFile)) {
    $OutFile = Join-Path $repoRoot "改进意见.md"
}

function Resolve-Adb {
    param([string]$Explicit)

    if (-not [string]::IsNullOrWhiteSpace($Explicit)) {
        if (Test-Path -LiteralPath $Explicit) { return $Explicit }
        Write-Host "指定的 adb 不存在：$Explicit" -ForegroundColor Yellow
        return $null
    }

    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk\platform-tools\adb.exe"),
        "C:\Android\Sdk\platform-tools\adb.exe",
        "/usr/bin/adb"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

$adbPath = Resolve-Adb -Explicit $Adb
if (-not $adbPath) {
    Write-Host "找不到 adb。装一份 Android SDK 的 platform-tools，或者用 -Adb 指定 adb.exe 的位置。" -ForegroundColor Yellow
    exit 1
}

# 只认已授权的设备；没连、没授权、连了多台都要说清楚，别让人对着「取不到」发呆。
# 注意用 @() 兜住：只有一台设备时，管道的返回值会退化成字符串，
# 那时 $devices[0] 取到的是第一个字符，不是设备号。
$devices = @(
    & $adbPath devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "^\S+\s+device$" } |
        ForEach-Object { ($_ -split "\s+")[0] }
)

if (-not $devices) {
    Write-Host "没有检测到已授权的手机。" -ForegroundColor Yellow
    Write-Host "请用数据线连上手机，并在手机上允许这台电脑的 USB 调试。"
    Write-Host "当前 adb 看到的设备状态如下："
    & $adbPath devices
    exit 2
}

if ($devices.Count -gt 1) {
    Write-Host "检测到多台设备，用第一台：$($devices[0])"
}
$serial = $devices[0]

$targetDir = Split-Path -Parent $OutFile
if ($targetDir -and -not (Test-Path -LiteralPath $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

function Get-RemoteFile([string]$Serial, [string]$Destination) {
    # 正常情况：文件名直接拉。文件名是中文，个别终端编码不配合时会失败，所以有下面的兜底。
    $null = & $adbPath -s $Serial pull $RemotePath $Destination 2>&1
    if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $Destination)) {
        return (Get-Item -LiteralPath $Destination).Length -gt 0
    }
    return $false
}

function Get-RemoteFileByGlob([string]$Serial, [string]$Destination) {
    # 兜底：不在参数里写中文文件名，改让手机自己去匹配 —— 下载目录里的 .md
    # 就是这个应用写的；同时把 adb 的输出直接重定向进文件，绕开各终端的编码差异。
    $temp = [System.IO.Path]::GetTempFileName()
    try {
        $commandLine = '"' + $adbPath + '" -s ' + $Serial +
            ' exec-out sh -c "cat /sdcard/Download/*.md" > "' + $temp + '"'
        cmd /c $commandLine | Out-Null
        if ((Test-Path -LiteralPath $temp) -and (Get-Item -LiteralPath $temp).Length -gt 0) {
            Move-Item -LiteralPath $temp -Destination $Destination -Force
            return $true
        }
        return $false
    } finally {
        if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue }
    }
}

$ok = Get-RemoteFile -Serial $serial -Destination $OutFile
if (-not $ok) {
    $ok = Get-RemoteFileByGlob -Serial $serial -Destination $OutFile
}

if (-not $ok) {
    Write-Host "手机上没找到意见文件（$RemotePath）。" -ForegroundColor Yellow
    Write-Host "先在手机上打开：生活助理 → 设置 → 改进意见，记一条试试。"
    exit 3
}

Write-Host "已取回：$OutFile" -ForegroundColor Green
Write-Host ""
Get-Content -LiteralPath $OutFile -Encoding UTF8
