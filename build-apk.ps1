# 1-Click PowerShell Build Script for Pixous HR Mobile APK
Set-Location -Path "$PSScriptRoot\flutter_mobile"
Write-Host "==> Fetching Flutter packages..." -ForegroundColor Cyan
flutter pub get
Write-Host "==> Building Release APK..." -ForegroundColor Cyan
flutter build apk --release
Write-Host "==> SUCCESS! Your APK is ready at: $PSScriptRoot\flutter_mobile\build\app\outputs\flutter-apk\app-release.apk" -ForegroundColor Green
