@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === TRINITY v4.0 push-to-github ===
set /p REPO="GitHub repo URL (e.g. https://github.com/USERNAME/trinity-v4.git): "
git remote remove origin 2>nul
git remote add origin %REPO%
git push -u origin main
echo.
echo 完成。如果提示输入账号密码，使用你的 GitHub 用户名 + Personal Access Token（不要用登录密码）。
pause
