plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.lyricsapp.receiver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lyricsapp.receiver"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.1-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("${rootDir}/release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "lyricspass"
                keyAlias = "lyricskey"
                keyPassword = "lyricspass"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        // Compose 1.5's UAST detectors crash in AGP 8.7 / Kotlin 2.0 analysis
        // while visiting existing Compose code; disable only the affected detectors.
        disable += setOf("MutableCollectionMutableState", "AutoboxingStateCreation")
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")

    // Compose
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-graphics:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")

    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // Image loading (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Video playback (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
}
