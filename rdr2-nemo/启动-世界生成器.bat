@echo off
rem ============================================================
rem  TRINITY 世界生成器 · RDR2 一键启动
rem  1. 用 Steam 启动游戏（你的游戏是 Steam 版）
rem  2. 自动关掉可能弹出的 Rockstar 登录窗口（游戏会继续离线加载）
rem  3. 进故事模式后，世界自动开始生成事件
rem ============================================================
echo [TRINITY] 正在通过 Steam 启动 RDR2...
start steam://rungameid/1174180

echo [TRINITY] 等待游戏启动（30 秒后自动处理 Rockstar 登录窗口）...
timeout /t 30 /nobreak >nul

echo [TRINITY] 检查 Rockstar 登录窗口...
powershell -NoProfile -Command "Get-Process SocialClubHelper -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | ForEach-Object { $_.CloseMainWindow(); Write-Host ('[TRINITY] 已关闭登录窗口: ' + $_.Id) }"

echo [TRINITY] 游戏继续加载中。进入故事模式后，世界将自动生成：
echo        下雪/起雾/雷暴 · 鹿群 · 狼群 · 秃鹫 · 野马 · 光柱标记
echo [TRINITY] F8 环境开关 / F9 状态 HUD
pause
