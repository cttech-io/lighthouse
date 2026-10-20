plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "io.cttech.lighthouse"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.cttech.lighthouse"
        minSdk = 30
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.2"
        resValue("string", "app_name", "Lighthouse")
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
            // Distinguishable in the "Default home app" list next to the optimised build.
            resValue("string", "app_name", "Lighthouse Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release-optimised but debug-signed: use this to judge smoothness (debug builds are much slower).
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".profile"
            versionNameSuffix = "-profile"
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    // Exported schemas are committed so future migrations can be tested.
    schemaDirectory("$projectDir/schemas")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network) // cover-picker thumbnails
    implementation(libs.zxing.core) // QR code for "Send key from phone" (Apache-2.0)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
