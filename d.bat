@echo off
node fix-imports.js
if "%~1"=="" (
    powershell -ExecutionPolicy Bypass -File .\auto-push-deploy.ps1 -Message "Auto push and deploy"
) else (
    powershell -ExecutionPolicy Bypass -File .\auto-push-deploy.ps1 -Message "%*"
)
