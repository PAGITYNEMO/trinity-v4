@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
chcp 65001 >nul
if not exist build mkdir build
if exist sources.txt del sources.txt
for /r src %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!" >> sources.txt
)
javac -encoding UTF-8 -d build @sources.txt
if errorlevel 1 goto :err
java "-Dfile.encoding=UTF-8" -cp build trinity.Main %*
goto :eof
:err
echo BUILD FAILED
exit /b 1
