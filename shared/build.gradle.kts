plugins {
    alias(libs.plugins.android.library)
    kotlin("android")
}

android {
    namespace = "mx.utng.deliverytrack.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
