@echo off
title Building Pixous HR Mobile App Release APK...
echo.
echo ============================================================
echo   BUILDING RELEASE APK FOR FLUTTER MOBILE APP...
echo ============================================================
echo.

set "PATH=%PATH%;C:\Windows\System32\WindowsPowerShell\v1.0\;C:\Windows\System32\"

pushd "%~dp0flutter_mobile"

echo Current Directory: %CD%
echo.
echo 1. Fetching Packages...
call flutter pub get

echo.
echo 2. Building Android Release APK...
call flutter build apk --release

echo.
popd
pause
