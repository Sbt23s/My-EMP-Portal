#!/usr/bin/env bash
echo "============================================================"
echo "  BUILDING PIXOUS HR PORTAL 100% IOS APP (.IPA / XCODE)"
echo "============================================================"
echo ""

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR/flutter_mobile"

echo "1. Fetching Flutter Packages..."
flutter pub get

echo "2. Installing CocoaPods Dependencies..."
cd ios
pod install
cd ..

echo "3. Compiling Release iOS App (.ipa archive)..."
flutter build ipa --release --no-codesign

echo ""
echo "============================================================"
echo "  SUCCESS! YOUR IOS APP ARCHIVE IS READY AT:"
echo "  $DIR/flutter_mobile/build/ios/archive/Runner.xcarchive"
echo "============================================================"
