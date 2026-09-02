@echo off
title Launching Pixous HR Mobile App Live Preview in Chrome...
echo.
echo ============================================================
echo   LAUNCHING LIVE CHROME PREVIEW FOR FLUTTER MOBILE APP...
echo ============================================================
echo.

set "PATH=%PATH%;C:\Windows\System32\WindowsPowerShell\v1.0\;C:\Windows\System32\"

pushd "%~dp0flutter_mobile"

echo Current Directory: %CD%
echo.
if not exist "web\index.html" (
    echo Configured web platform...
    call flutter create --platforms=web,android,ios .
)

echo 1. Fetching Packages...
call flutter pub get

echo.
echo 2. Launching Live Mobile App in Chrome Browser...
call flutter run -d chrome --web-browser-flag "--disable-web-security"

echo.
popd
pause
