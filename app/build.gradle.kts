plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.compose)
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("C:\\Users\\Admin\\.android\\debug.keystore")
            storePassword = "android"
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
        }
    }
    namespace = "com.manish.demo"
    compileSdk {
        version = release(34)
    }

    defaultConfig {
        applicationId = "com.manish.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    dependencies {
        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

        // Networking
        implementation ("com.squareup.retrofit2:retrofit:2.9.0")
        implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

        // Image Loading (For Posters)
        implementation ("io.coil-kt:coil-compose:2.4.0") // If using Compose
        // OR implementation "io.coil-kt:coil:2.4.0" // If using XML

        // Coroutines for background tasks
        implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    }
    // ✅ Firebase BOM (Bill of Materials) - manages compatible versions
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))  // Updated

    // Firebase dependencies
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // ✅ Compose BOM - Single source of truth for Compose versions
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))  // Updated

    // Compose dependencies (versions managed by BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")  // Remove version
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ✅ SwipeRefresh - Using Compose Foundation instead of Accompanist
    implementation("androidx.compose.foundation:foundation")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.34.0")
    // ✅ Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")  // Keep only one

    // ✅ Media Player (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")  // Consolidated versions
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")

    // ✅ Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // ✅ Image Picker
    implementation("com.github.dhaval2404:imagepicker:2.1")

    // ✅ Image Compression
    implementation("id.zelory:compressor:3.0.1")

    // AndroidX dependencies
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.0")
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Fragment/Activity support
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Material Components (for non-Compose UI if needed)
    implementation("com.google.android.material:material:1.12.0")

    // Remove these - Not needed or duplicate:
//    implementation("com.google.firebase:firebase-vertexai")
//    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.0.0-alpha10")
//    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}