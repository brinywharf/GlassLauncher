import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.glasslauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.brinywharf.glasslauncher"
        // Android 13+: the liquid-glass lens effect needs RuntimeShader (API 33).
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-alpha"
    }

    // One fixed key so every new APK installs over the previous one.
    // (A debug-style key checked into the repo: fine for an alpha, NOT for a store release.)
    signingConfigs {
        create("shared") {
            storeFile = file("glass-debug.keystore")
            storePassword = "glasslauncher"
            keyAlias = "glass"
            keyPassword = "glasslauncher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Liquid glass: refraction (lens), blur, vibrancy, highlights
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
}
