# 性能验证脚本（HANDOFF_PERF.md 配套）
# 用法：
#   1. 手机连接 USB（序列号 39HUN24525G05831），开启 USB 调试
#   2. powershell -ExecutionPolicy Bypass -File verify_perf.ps1 -Step install   # 安装新 APK
#   3. 手动进入统计页/设置页，然后：
#      powershell -ExecutionPolicy Bypass -File verify_perf.ps1 -Step gfx      # 掉帧率基准（先 reset 再手动滚动 30 秒）
#   4. 截图对比视觉零变化：
#      powershell -ExecutionPolicy Bypass -File verify_perf.ps1 -Step shot -Name stats_before
#      （换装另一版本后）powershell -ExecutionPolicy Bypass -File verify_perf.ps1 -Step diff -A stats_before -B stats_after
param(
    [Parameter(Mandatory=$true)][string]$Step,
    [string]$Serial = "39HUN24525G05831",
    [string]$Name = "shot",
    [string]$A = "",
    [string]$B = "",
    [int]$DiffThreshold = 8
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.aistudio.novelreader.kxmpzq"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
$outDir = Join-Path $PSScriptRoot ".perf_shots"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

function Adb([string[]]$Args) {
    $full = @("-s", $Serial) + $Args
    & $adb @full
}

switch ($Step) {

    "install" {
        Write-Host "== 安装 $apk =="
        Adb @("install", "-r", "`"$apk`"")
        Write-Host "== 启动应用 =="
        Adb @("shell", "monkey", "-p", $pkg, "-c", "android.intent.category.LAUNCHER", "1")
    }

    "gfxreset" {
        Adb @("shell", "dumpsys", "gfxinfo", $pkg, "reset")
        Write-Host "已重置。现在手动滚动目标页面约 30 秒，然后运行: verify_perf.ps1 -Step gfx"
    }

    "gfx" {
        $raw = Adb @("shell", "dumpsys", "gfxinfo", $pkg) | Out-String
        foreach ($line in ($raw -split "`n")) {
            if ($line -match "(Total frames rendered|Janky frames|50th|90th|95th|99th|Number Slow|Number Deadline)") {
                Write-Host $line.Trim()
            }
        }
    }

    "shot" {
        $remote = "/sdcard/perf_shot.png"
        $local = Join-Path $outDir "$Name.png"
        Adb @("shell", "screencap", "-p", $remote)
        Adb @("pull", $remote, "`"$local`"")
        Adb @("shell", "rm", $remote)
        Write-Host "截图已保存: $local"
    }

    "diff" {
        Add-Type -AssemblyName System.Drawing
        $pa = Join-Path $outDir "$A.png"
        $pb = Join-Path $outDir "$B.png"
        $ia = [System.Drawing.Bitmap]::FromFile($pa)
        $ib = [System.Drawing.Bitmap]::FromFile($pb)
        if (($ia.Width -ne $ib.Width) -or ($ia.Height -ne $ib.Height)) {
            Write-Host "尺寸不一致: $($ia.Width)x$($ia.Height) vs $($ib.Width)x$($ib.Height)"
            return
        }
        $rect = New-Object System.Drawing.Rectangle(0, 0, $ia.Width, $ia.Height)
        $da = $ia.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $db = $ib.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $bytes = [Math]::Abs($da.Stride) * $ia.Height
        $ba = New-Object byte[] $bytes; $bb = New-Object byte[] $bytes
        [System.Runtime.InteropServices.Marshal]::Copy($da.Scan0, $ba, 0, $bytes)
        [System.Runtime.InteropServices.Marshal]::Copy($db.Scan0, $bb, 0, $bytes)
        $ia.UnlockBits($da); $ib.UnlockBits($db)
        $total = $ia.Width * $ia.Height
        $diffPx = 0; $maxDelta = 0
        for ($i = 0; $i -lt $bytes; $i += 4) {
            $d = [Math]::Max([Math]::Abs($ba[$i]-$bb[$i]), [Math]::Max([Math]::Abs($ba[$i+1]-$bb[$i+1]), [Math]::Abs($ba[$i+2]-$bb[$i+2])))
            if ($d -gt $maxDelta) { $maxDelta = $d }
            if ($d -gt $DiffThreshold) { $diffPx++ }
        }
        $pct = [math]::Round(100.0 * $diffPx / $total, 4)
        Write-Host "差异像素(>=$DiffThreshold): $diffPx / $total ($pct%)  最大通道差: $maxDelta"
        if ($pct -lt 0.01) { Write-Host "结论：视觉零变化 PASS" } else { Write-Host "结论：存在可见差异，请人工核对 $outDir" }
        $ia.Dispose(); $ib.Dispose()
    }

    default { Write-Host "未知 Step: $Step（可用: install / gfxreset / gfx / shot / diff）" }
}
