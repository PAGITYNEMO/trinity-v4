$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Stop'
$base = "G:\TRINITY v4.0\minecraft-client\.minecraft"
$mc = Get-Content "$base\versions\1.21.4\1.21.4.json" -Raw -Encoding utf8 | ConvertFrom-Json
$prof = Get-Content "$base\versions\fabric-loader-0.19.3-1.21.4\fabric-loader-0.19.3-1.21.4.json" -Raw -Encoding utf8 | ConvertFrom-Json

function Test-WinRules($lib) {
    if (-not $lib.rules) { return $true }
    $allow = $false
    foreach ($r in $lib.rules) {
        $match = $true
        if ($r.os) {
            if ($r.os.name -and $r.os.name -ne 'windows') { $match = $false }
            if ($r.os.arch -and $r.os.arch -notin @('x86_64', 'x64')) { $match = $false }
        }
        if ($r.action -eq 'allow' -and $match) { $allow = $true }
        if ($r.action -eq 'disallow' -and $match) { $allow = $false }
    }
    return $allow
}

# ---- 1. natives: download :natives-windows jars, extract dlls ----
Write-Output "== natives =="
foreach ($lib in $mc.libraries) {
    if ($lib.name -notmatch ':natives-windows$') { continue }
    if (-not (Test-WinRules $lib)) { continue }
    $parts = $lib.name -split ':'
    $group = ($parts[0] -replace '\.', '/')
    $art = $parts[1]; $ver = $parts[2]
    $rel = "$group/$art/$ver/$art-$ver-natives-windows.jar"
    $dest = "$base\libraries\$rel"
    if (-not (Test-Path $dest)) {
        $dir = Split-Path $dest -Parent
        New-Item -ItemType Directory -Force $dir | Out-Null
        Invoke-WebRequest -Uri $lib.downloads.artifact.url -OutFile $dest -UseBasicParsing -TimeoutSec 180
    }
    # extract
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($dest)
    foreach ($e in $zip.Entries) {
        if ($e.FullName -match '\.(dll|so|dylib)$') {
            $out = Join-Path "$base\natives" (Split-Path $e.FullName -Leaf)
            if (-not (Test-Path $out)) {
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $out, $true)
            }
        }
    }
    $zip.Dispose()
    Write-Output "natives: $art"
}

# ---- 2. flat classpath dir ----
Write-Output "== classpath =="
$cpDir = "G:\TRINITY v4.0\minecraft-client\classpath"
New-Item -ItemType Directory -Force $cpDir | Out-Null
$missing = @()
$count = 0
foreach ($lib in $mc.libraries) {
    if (-not (Test-WinRules $lib)) { continue }
    if ($lib.name -match ':natives-') { continue } # natives jars are extracted, not classpathed
    if ($lib.name -match '^org\.ow2\.asm:') { continue } # fabric provides newer ASM (9.10.1)
    $parts = $lib.name -split ':'
    $group = ($parts[0] -replace '\.', '/')
    $art = $parts[1]; $ver = $parts[2]
    $rel = "$group/$art/$ver/$art-$ver.jar"
    $src = "$base\libraries\$rel"
    $flat = "$cpDir\$($art)-$ver.jar"
    if (Test-Path $src) {
        if (-not (Test-Path $flat)) { Copy-Item $src $flat }
        $count++
    } else { $missing += $rel }
}
foreach ($lib in $prof.libraries) {
    $parts = $lib.name -split ':'
    $group = ($parts[0] -replace '\.', '/')
    $art = $parts[1]; $ver = $parts[2]
    $rel = "$group/$art/$ver/$art-$ver.jar"
    $src = "$base\libraries\$rel"
    $flat = "$cpDir\$($art)-$ver.jar"
    if (Test-Path $src) {
        if (-not (Test-Path $flat)) { Copy-Item $src $flat }
        $count++
    } else { $missing += $rel }
}
Copy-Item "$base\versions\1.21.4\1.21.4.jar" "$cpDir\minecraft-1.21.4.jar" -Force
Write-Output "classpath jars: $count, missing: $($missing.Count)"
$missing | ForEach-Object { Write-Output "MISSING: $_" }

# ---- 3. fabric-loader version jar (for HMCL compat) ----
$fl = "$base\libraries\net\fabricmc\fabric-loader\0.19.3\fabric-loader-0.19.3.jar"
if (Test-Path $fl) {
    Copy-Item $fl "$base\versions\fabric-loader-0.19.3-1.21.4\fabric-loader-0.19.3-1.21.4.jar" -Force
    Write-Output "fabric version jar copied"
}

# ---- 4. mods ----
$api = Get-ChildItem -Recurse -Filter "fabric-api-0.119.4*.jar" "$env:USERPROFILE\.gradle\caches\modules-2" | Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if ($api) { Copy-Item $api.FullName "$base\mods\fabric-api-0.119.4+1.21.4.jar" -Force; Write-Output "fabric-api -> mods" }
Copy-Item "G:\TRINITY v4.0\minecraft-mod\build\libs\trinity-noise-4.0.0.jar" "$base\mods\trinity-noise-4.0.0.jar" -Force
Write-Output "trinity-noise -> mods"

# ---- 5. launch command ----
$java = "G:\TRINITY v4.0\_jdk21\jdk-21.0.12+8\bin\java.exe"
$natives = "$base\natives"
$cp = "$cpDir\*;$base\versions\1.21.4\1.21.4.jar"

$vars = @{
    '${natives_directory}' = $natives
    '${launcher_name}' = 'trinity-noise'
    '${launcher_version}' = '4.0.0'
    '${classpath}' = $cp
    '${auth_player_name}' = 'Trinity'
    '${version_name}' = 'fabric-loader-0.19.3-1.21.4'
    '${game_directory}' = $base
    '${assets_root}' = "$base\assets"
    '${assets_index_name}' = '19'
    '${auth_uuid}' = '00000000-0000-0000-0000-000000000000'
    '${auth_access_token}' = '0'
    '${clientid}' = 'trinity-noise'
    '${auth_xuid}' = '0'
    '${user_type}' = 'legacy'
    '${version_type}' = 'release'
    '${user_properties}' = '{}'
    '${resolution_width}' = '1280'
    '${resolution_height}' = '720'
    '${auth_session}' = '0'
    '${quickPlayPath}' = ''
}

function Resolve-Args($list) {
    $out = @()
    foreach ($a in $list) {
        if ($a -isnot [string]) { continue }
        $s = $a
        foreach ($k in $vars.Keys) { $s = $s.Replace($k, $vars[$k]) }
        $out += $s
    }
    return $out
}

$jvmArgs = Resolve-Args $mc.arguments.jvm
$gameArgs = Resolve-Args $mc.arguments.game
$main = $prof.mainClass

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('@echo off')
[void]$sb.AppendLine('chcp 65001 >nul')
[void]$sb.AppendLine("set JAVA=$java")
[void]$sb.AppendLine('cd /d "%~dp0"')

# quote every token containing a space so cmd passes it as one argument
function Quote-Tokens([string[]]$argsList) {
    $out = @()
    foreach ($a in $argsList) {
        if ($a -match ' ') { $out += '"' + $a + '"' } else { $out += $a }
    }
    return $out
}
$all = @('-Xmx3G') + $jvmArgs + @($main) + $gameArgs
$quoted = Quote-Tokens $all
[void]$sb.AppendLine('"%JAVA%" ' + ($quoted -join ' '))
[void]$sb.AppendLine('pause')
$sb.ToString() | Set-Content "G:\TRINITY v4.0\minecraft-client\start-offline.bat" -Encoding ascii
Write-Output "start-offline.bat written (quoted)"
Write-Output "CMD: $($quoted -join ' ')"
