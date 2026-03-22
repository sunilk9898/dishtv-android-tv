plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

// ─── Auto-versioning from version.properties ─────────────
val versionPropsFile = rootProject.file("version.properties")
val versionProps = java.util.Properties()
if (versionPropsFile.exists()) {
    versionProps.load(versionPropsFile.inputStream())
}
val vMajor = (versionProps["VERSION_MAJOR"] as? String)?.toIntOrNull() ?: 1
val vMinor = (versionProps["VERSION_MINOR"] as? String)?.toIntOrNull() ?: 0
val vPatch = (versionProps["VERSION_PATCH"] as? String)?.toIntOrNull() ?: 0
val vBuild = (versionProps["VERSION_BUILD"] as? String)?.toIntOrNull() ?: 1

android {
    namespace = "com.dishtv.commandcenter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dishtv.commandcenter"
        minSdk = 24          // Android TV 7.0+
        targetSdk = 34
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = "$vMajor.$vMinor.$vPatch"

        // Server configuration
        buildConfigField("String", "BASE_URL", "\"https://dishtv.io\"")
        buildConfigField("String", "WS_URL", "\"wss://dishtv.io/ws\"")
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/dishtv-release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "DishTV@2026Prod"
                keyAlias = System.getenv("KEY_ALIAS") ?: "dishtv-release"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "DishTV@2026Prod"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "BASE_URL", "\"https://dishtv.io\"")
            buildConfigField("String", "WS_URL", "\"wss://dishtv.io/ws\"")
        }
        release {
            isMinifyEnabled = false  // Disabled for now — enable after ProGuard rules are tested
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://dishtv.io\"")
            buildConfigField("String", "WS_URL", "\"wss://dishtv.io/ws\"")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Auto-increment build number after each build
    applicationVariants.all {
        if (name == "release") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
                output.outputFileName = "DishTV-CommandCenter-v${versionName}-build${vBuild}.apk"
            }
        }
        if (name == "debug") {
            outputs.all {
                val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
                output.outputFileName = "DishTV-CommandCenter-v${versionName}-debug-build${vBuild}.apk"
            }
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
        buildConfig = true
        viewBinding = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // ─── Android Core ──────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ─── Android TV / Leanback ─────────────────────────────
    implementation("androidx.leanback:leanback:1.2.0-alpha04")
    implementation("androidx.tvprovider:tvprovider:1.0.0")

    // ─── Lifecycle (ViewModel + LiveData + Coroutines) ─────
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // ─── Kotlin Coroutines ─────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ─── Hilt (Dependency Injection) ───────────────────────
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    // ─── Networking (OkHttp + Retrofit) ────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ─── JSON Parsing ──────────────────────────────────────
    implementation("com.google.code.gson:gson:2.10.1")

    // ─── ExoPlayer / Media3 (Video Playback) ──────────────
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")

    // ─── WebRTC (Live Casting) ─────────────────────────────
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // ─── Image Loading ─────────────────────────────────────
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // ─── WebView ───────────────────────────────────────────
    implementation("androidx.webkit:webkit:1.9.0")

    // ─── Work Manager (Background Tasks) ──────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ─── Security ──────────────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ─── Testing ───────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

kapt {
    correctErrorTypes = true
}

// ─── Auto-increment build number task ──────────────────────
tasks.register("incrementBuildNumber") {
    doLast {
        val props = java.util.Properties()
        val file = rootProject.file("version.properties")
        if (file.exists()) props.load(file.inputStream())
        val build = ((props["VERSION_BUILD"] as? String)?.toIntOrNull() ?: 0) + 1
        props["VERSION_BUILD"] = build.toString()
        props.store(file.outputStream(), "Auto-incremented build number")
        println("Build number incremented to $build")
    }
}
