$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Stop'
$base = "G:\TRINITY v4.0\minecraft-client\.minecraft"
$vd = Get-Content "G:\TRINITY v4.0\_mc1214.json" -Raw -Encoding utf8 | ConvertFrom-Json

$ok = 0; $skip = 0; $fail = 0
foreach ($lib in $vd.libraries) {
    # rules: windows only
    if ($lib.rules) {
        $allow = $false
        foreach ($r in $lib.rules) {
            $match = $true
            if ($r.os) {
                if ($r.os.name -and $r.os.name -ne 'windows') { $match = $false }
                if ($r.os.arch -and $r.os.arch -ne 'x86_64' -and $r.os.arch -ne 'x64') { $match = $false }
            }
            if ($r.action -eq 'allow' -and $match) { $allow = $true }
            if ($r.action -eq 'disallow' -and $match) { $allow = $false }
        }
        if (-not $allow) { $skip++; continue }
    }
    $name = $lib.name
    $parts = $name -split ':'
    $group = ($parts[0] -replace '\.', '/')
    $art = $parts[1]
    $ver = $parts[2]
    $relPath = "$group/$art/$ver/$art-$ver.jar"

    if ($lib.downloads.artifact) {
        $dest = "$base\libraries\$relPath"
        $dir = Split-Path $dest -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
        if (-not (Test-Path $dest)) {
            try {
                Invoke-WebRequest -Uri $lib.downloads.artifact.url -OutFile $dest -UseBasicParsing -TimeoutSec 120
                $ok++
            } catch { $fail++; Write-Output "FAIL $name : $($_.Exception.Message)" }
        } else { $ok++ }
    }
    # natives classifier for windows
    if ($lib.natives.windows) {
        $cls = $lib.natives.windows
        $classifier = $lib.downloads.classifiers.$cls
        if ($classifier) {
            $cpath = "$group/$art/$ver/$art-$ver-$cls.jar"
            $dest = "$base\libraries\$cpath"
            $dir = Split-Path $dest -Parent
            if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
            if (-not (Test-Path $dest)) {
                try {
                    Invoke-WebRequest -Uri $classifier.url -OutFile $dest -UseBasicParsing -TimeoutSec 180
                } catch { Write-Output "FAIL natives $name : $($_.Exception.Message)" }
            }
        }
    }
}
Write-Output "libraries done: ok=$ok skip=$skip fail=$fail"
