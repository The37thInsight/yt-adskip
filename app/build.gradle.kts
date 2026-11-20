import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release key lives outside the repo: ~/.android-keys/yt-adskip.properties (storeFile=..., password=...)
val keyProps = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.android-keys/yt-adskip.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.the37thinsight.ytadskip"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.the37thinsight.ytadskip"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.3.0"
    }

    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("password")
                keyAlias = "yt-adskip"
                keyPassword = keyProps.getProperty("password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // android.jar's org.json is a stub on the JVM
}
