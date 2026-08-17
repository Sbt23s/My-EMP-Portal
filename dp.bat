@echo off
echo ==> Running Quick Auto Push & Deploy Pipeline...
node fix-imports.js
if "%~1"=="" (
    powershell -ExecutionPolicy Bypass -File .\auto-push-deploy.ps1 -Message "Quick auto push and deploy"
) else (
    powershell -ExecutionPolicy Bypass -File .\auto-push-deploy.ps1 -Message "%*"
)
