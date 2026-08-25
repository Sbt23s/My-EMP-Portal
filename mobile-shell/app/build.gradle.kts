plugins {
    id("com.android.application")
}

android {
    namespace = "com.pixous.hrportal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixous.hrportal"
        // 24 covers Android 7 and up. The portal needs a WebView that can do
        // WebRTC and modern JavaScript, and below this the system WebView on
        // devices that never update is too old to run the app at all.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // Not shrunk. There is almost no Kotlin here -- the app is a
            // window onto the website -- so shrinking saves nothing worth the
            // risk of it removing something the WebView needs reflectively.
            isMinifyEnabled = false
            // Debug signing so the APK installs without a keystore. A Play
            // Store release needs a real signing key; this is for sideloading.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
