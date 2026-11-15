@file:Suppress("DEPRECATION")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Release secrets must never be committed. All four variables are required to sign
// production APKs; without them Gradle can still build and test debug variants.
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val allowUnsignedRelease = providers.gradleProperty("allowUnsignedRelease")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)


android {
    namespace = "com.example.on_device_qwen"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.on_device_qwen"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF"
                )
            }
        }
    }

    // Local and CI debug builds use Android's automatically generated debug keystore.
    // Production releases use a separately managed keystore via environment variables.
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep"
            )
            ndk {
                abiFilters.add("arm64-v8a")
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Leave debug signing at the Android Gradle Plugin default.
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

val configuredReleaseKeystorePath = releaseKeystorePath?.let { file(it).absolutePath }
val missingReleaseSigning = !hasReleaseSigning && !allowUnsignedRelease

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "packageRelease" || name == "bundleRelease") {
        val localMissingSigning = missingReleaseSigning
        val localKeystorePath = configuredReleaseKeystorePath
        doFirst {
            if (localMissingSigning) {
                throw GradleException(
                    "Release build requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
                        "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD. Pass -PallowUnsignedRelease=true " +
                        "only for CI verification builds."
                )
            }
            if (localKeystorePath != null && !File(localKeystorePath).isFile) {
                throw GradleException(
                    "ANDROID_KEYSTORE_PATH points to a missing file: $localKeystorePath"
                )
            }
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Jetpack Compose (BOM for version alignment)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Activity & Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // LiteRT-LM — On-device LLM inference engine
    implementation(libs.litert.lm)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}