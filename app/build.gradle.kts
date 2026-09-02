plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.floatingdpad"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.floatingdpad"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            // Point the debug config at an explicit file rather than trusting AGP to
            // find ~/.android/debug.keystore -- it resolves that through
            // ANDROID_USER_HOME / user.home, which does not agree with $HOME on a CI
            // runner. When the file is absent (a normal local checkout) AGP falls back
            // to its usual auto-generated keystore, so local builds are unaffected.
            val shared = rootProject.file("debug.keystore")
            if (shared.exists()) {
                storeFile = shared
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildFeatures {
        // AGP 8 disables AIDL by default; IKeyInjector needs it.
        aidl = true
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Deliberately off: the Shizuku user service is instantiated by name in a
            // separate process, and this is a sideloaded personal app with no size budget.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
