pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

plugins {
    // Android only. The Kotlin plugin was here and failed to apply against
    // this Gradle and AGP pair, and a window onto a website has no need of
    // Kotlin -- dropping it removes a whole toolchain from the build rather
    // than pinning versions against each other.
    id("com.android.application") version "9.0.1" apply false
}

rootProject.name = "Pixous HR"
include(":app")
