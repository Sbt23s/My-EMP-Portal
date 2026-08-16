plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.pixous.hr_portal_mobile"
    // Pinned, not inherited.
    //
    // flutter_webrtc requires 35 and the Flutter default here is lower, so the
    // build fails at the very end with a message about compileSdk rather than
    // about the plugin that needs it. Raising compileSdk only lets newer APIs
    // be compiled against — targetSdk below still decides runtime behaviour and
    // minSdk still decides which phones can install it, so this changes nothing
    // for anybody's device.
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        // Required by flutter_local_notifications, which uses java.time to
        // schedule. Without it the release build fails outright — the message
        // names the dependency but not the fix, and the app compiles fine right
        // up until the APK is assembled.
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.pixous.hr_portal_mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    packaging {
        resources {
            // Two plugins can ship the same licence file; without this the
            // merge step fails on a duplicate that has nothing to do with code.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // The desugaring runtime itself. Pinned rather than left to a range: a
    // version bump here changes what the APK ships, and that should be a
    // decision rather than a surprise on the next build.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
