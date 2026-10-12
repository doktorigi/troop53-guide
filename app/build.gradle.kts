plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.doktorigi.troop53"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.doktorigi.troop53"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.doktorigi.troop53.IllustrationChecks"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }

// Content and vector integrity tests must rerun after asset-only edits, too.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.dir("src/main/assets")
    inputs.dir("src/main/res/drawable")
}
