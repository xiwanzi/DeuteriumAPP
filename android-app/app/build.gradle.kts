plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.deuterium.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.deuterium.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.3"
    }

    buildTypes {
        debug {
            buildConfigField("String", "HTTP_BASE_URL", "\"http://example.com:80/api/v1/\"")
            buildConfigField("String", "CHAT_WS_URL", "\"ws://example.com:80/api/v1/chat/ws\"")
            manifestPlaceholders["cleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "HTTP_BASE_URL", "\"https://example.com/api/v1/\"")
            buildConfigField("String", "CHAT_WS_URL", "\"wss://example.com/api/v1/chat/ws\"")
            manifestPlaceholders["cleartextTraffic"] = "false"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.animation:animation:1.10.3")
    implementation("androidx.compose.foundation:foundation:1.10.3")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8")
    implementation("androidx.compose.material3:material3-android:1.4.0")
    implementation("androidx.compose.ui:ui:1.10.3")
    implementation("androidx.compose.ui:ui-graphics:1.10.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.10.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.3")

    testImplementation("junit:junit:4.13.2")
}

