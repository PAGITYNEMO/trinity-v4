@echo off
rem ============================================================
rem  TRINITY 西部 · RDR2 一键启动器
rem  1. 通过 Steam 启动 RDR2（你的游戏是 Steam 版）
rem  2. 持续 90 秒监听并自动关闭 Rockstar 登录窗口（游戏继续离线加载）
rem  3. 进入故事模式后，世界自动开始工作
rem ============================================================
title TRINITY 西部启动器
color 0A
echo.
echo   [TRINITY v4.0] 正在通过 Steam 启动 Red Dead Redemption 2 ...
echo.
start steam://rungameid/1174180

echo   [TRINITY] 正在监听 Rockstar 登录窗口（自动关闭，持续 90 秒）...
echo.
powershell -NoProfile -Command "$d=(Get-Date).AddSeconds(90); while((Get-Date)-lt $d){ Get-Process SocialClubHelper -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | ForEach-Object { $_.CloseMainWindow(); Write-Host ('  [TRINITY] 已自动关闭登录窗口: ' + $_.Id) }; Start-Sleep 3 }"

echo.
echo   [TRINITY] 启动流程完成。游戏加载中，进故事模式后：
echo.
echo     光幕粒子     —— 136 个数学光子环绕/原野/天顶，随呼吸心跳脉动
echo     对偶读数     —— 屏幕左下角：R/H/S/kappa + 涌现状态
echo     低语字幕     —— 屏幕中央：NEMO 句子与事件提示
echo     图纹节点     —— 远处季节色光柱 = 干涉图纹矿脉
echo     涟漪         —— 骑马冲刺撞动光幕
echo     F8 环境开关   F9 读数开关
echo.
echo   [TRINITY] 生成器日志：游戏目录 nemo.log
timeout /t 12 /nobreak >nul
exit
