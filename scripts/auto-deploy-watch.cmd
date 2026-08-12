@echo off
REM My-EMP-Portal auto-deploy watcher launcher (double-click me)
REM Starts a minimized window that watches this repo and auto-pushes
REM any code change to GitHub -> which auto-deploys to your AWS server.
REM Close that minimized window to stop it.

where bash >nul 2>nul
if errorlevel 1 (
  echo bash not found - please install Git for Windows first.
  pause
  exit /b 1
)

cd /d "%~dp0.."
start "HR-Portal Auto-Deploy Watcher" /min bash scripts/auto-deploy-watch.sh "%~dp0.."
echo Watcher started in a minimized window. Close that window to stop it.
timeout /t 3 >nul
