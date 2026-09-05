plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.2"
}

fun getGitCommitSha(): String {
    val envSha = System.getenv("GITHUB_SHA")
    if (!envSha.isNullOrBlank()) {
        return envSha.take(7)
    }
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
        val sha = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (sha.isNotBlank()) sha else "dev"
    } catch (e: Exception) {
        "dev"
    }
}

android {
    namespace = "com.akshay.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.akshay.musicplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "v1.2.0"

        val gitSha = getGitCommitSha()
        val buildTime = System.currentTimeMillis()
        buildConfigField("String", "GIT_COMMIT_SHA", "\"$gitSha\"")
        buildConfigField("Long", "BUILD_TIME_MILLIS", "${buildTime}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH") ?: "release-key.jks"
            val ksFile = file(ksPath)
            val altFile = file("app/$ksPath")
            val targetFile = if (ksFile.exists()) ksFile else altFile

            if (targetFile.exists()) {
                storeFile = targetFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }
}

dependencies {
    // Kotlin & Coroutines
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    
    // Media3 (ExoPlayer & MediaSession)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Image Loading
    implementation(libs.coil.compose)

    // Permissions (Accompanist)
    implementation(libs.accompanist.permissions)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Material Design
    implementation(libs.material)

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Networking & Serialization (Moshi for Google Drive backup, OkHttp for InnerTube)
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Google Auth & WorkManager
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Android YouTube Player (official IFrame wrapper, fixes Error 150/152)
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // Audio Tagging & Metadata embedding (pure Java, no native .so)
    implementation("com.github.AdrienPoupa:jaudiotagger:2.2.3")
}

