$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# 从 git credential manager 动态取凭据（不硬编码）
$cred = "protocol=https`nhost=github.com`n`n" | git credential fill
$user = ($cred | Select-String '^username=').ToString().Split('=')[1]
$token = ($cred | Select-String '^password=').ToString().Split('=')[1]
Write-Output "user: $user"

$headers = @{ Authorization = "token $token"; 'User-Agent' = 'trinity-v4-release' }

# 1. 创建公开仓库 trinity-v4（已存在则跳过）
$body = @{
    name = 'trinity-v4'
    description = 'TRINITY v4.0 噪声统一场论 — 数论地形生成：协议 / 独立引擎 / Minecraft Fabric 模组'
    private = $false
    has_issues = $true
    has_wiki = $false
} | ConvertTo-Json

try {
    $repo = Invoke-RestMethod -Method Post -Uri 'https://api.github.com/user/repos' -Headers $headers -Body $body -ContentType 'application/json' -TimeoutSec 60
    Write-Output "repo created: $($repo.full_name) ($($repo.html_url))"
} catch {
    $err = $_.ErrorDetails.Message | ConvertFrom-Json
    if ($err.errors -and $err.errors[0].message -match 'already exists') {
        Write-Output "repo already exists"
        $repo = Invoke-RestMethod -Method Get -Uri "https://api.github.com/repos/$user/trinity-v4" -Headers $headers -TimeoutSec 60
        Write-Output "existing: $($repo.full_name)"
    } else {
        Write-Output "CREATE FAILED: $($_.ErrorDetails.Message)"
        exit 1
    }
}

# 2. 推送本地 main
cd 'G:\TRINITY v4.0'
$remotes = git remote
if ($remotes -contains 'origin') { git remote remove origin | Out-Null }
git remote add origin "https://github.com/$user/trinity-v4.git"
Write-Output "pushing..."
git push -u origin main 2>&1
Write-Output "PUSH EXIT: $LASTEXITCODE"

# 3. 验证
if ($LASTEXITCODE -eq 0) {
    $info = Invoke-RestMethod -Method Get -Uri "https://api.github.com/repos/$user/trinity-v4" -Headers $headers -TimeoutSec 60
    Write-Output "=== 验证 ==="
    Write-Output "repo: $($info.full_name)"
    Write-Output "url: $($info.html_url)"
    Write-Output "default_branch: $($info.default_branch)"
    Write-Output "visibility: $($info.visibility)"
}
