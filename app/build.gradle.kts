plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.cttech.lighthouse"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.cttech.lighthouse"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }

    // Two distribution flavours share all code; only storage access differs.
    // sideload: "All files access" + real folder watching (GitHub/adb/Obtainium).
    // play:     folder-picker access + rescans only (kept building for a future Play Store release).
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "ALL_FILES_ACCESS", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALL_FILES_ACCESS", "false")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
