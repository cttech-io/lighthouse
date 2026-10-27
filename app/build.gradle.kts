import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Release signing: keystore.properties locally (git-ignored), LIGHTHOUSE_KEYSTORE_* env vars in CI.
// With neither, release builds come out unsigned. The key itself never lives in the repo.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val releaseStoreFile: String? = keystoreProps.getProperty("storeFile") ?: System.getenv("LIGHTHOUSE_KEYSTORE_FILE")
val releaseStorePassword: String? = keystoreProps.getProperty("storePassword") ?: System.getenv("LIGHTHOUSE_KEYSTORE_PASSWORD")

android {
    namespace = "io.cttech.lighthouse"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.cttech.lighthouse"
        minSdk = 30
        targetSdk = 37
        versionCode = 18
        versionName = "0.2.0"
        resValue("string", "app_name", "Lighthouse")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storeType = "pkcs12"
                storePassword = releaseStorePassword
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "lighthouse"
                keyPassword = keystoreProps.getProperty("keyPassword") ?: releaseStorePassword
            }
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
