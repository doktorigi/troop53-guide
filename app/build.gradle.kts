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
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "com.doktorigi.troop53.IllustrationChecks"
        // Troop chat: Firebase web API key from google-services.json (identifies the project; rules enforce access).
        val firebase = groovy.json.JsonSlurper().parse(file("google-services.json")) as Map<*, *>
        val key = ((((firebase["client"] as List<*>)[0] as Map<*, *>)["api_key"] as List<*>)[0] as Map<*, *>)["current_key"]
        buildConfigField("String", "FIREBASE_KEY", "\"$key\"")
    }
    buildFeatures { buildConfig = true }
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
