param([int]$Chunk = 0, [int]$Total = 4)
$ProgressPreference = 'SilentlyContinue'
$base = "G:\TRINITY v4.0\minecraft-client\.minecraft"
$idx = Get-Content "$base\assets\indexes\19.json" -Raw -Encoding utf8 | ConvertFrom-Json
$props = @($idx.objects.PSObject.Properties)
$n = $props.Count
for ($i = $Chunk; $i -lt $n; $i += $Total) {
    $p = $props[$i]
    $hash = $p.Value.hash
    $sub = $hash.Substring(0, 2)
    $dest = "$base\assets\objects\$sub\$hash"
    if (Test-Path $dest) { continue }
    $url = "https://resources.download.minecraft.net/$sub/$hash"
    $dir = Split-Path $dest -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 45
    } catch {
        # one retry
        try { Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 45 } catch {}
    }
}
Write-Output "chunk $Chunk done"
