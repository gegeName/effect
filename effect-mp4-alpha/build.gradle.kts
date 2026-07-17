plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.chat.effect.mp4.alpha"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
    }
}

dependencies {
    api(project(":effect-core"))

    api("com.github.bytedance:AlphaPlayer:1.2.1") {
        exclude("org.jetbrains.kotlin", "kotlin-android-extensions-runtime")
    }
    implementation("android.arch.lifecycle:runtime:1.1.1")
}

apply(from = "jitpack.gradle")
