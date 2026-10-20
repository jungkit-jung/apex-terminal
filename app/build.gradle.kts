import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.trading.terminal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trading.terminal"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        val localProps = rootProject.file("local.properties")
        val props = Properties()
        if (localProps.exists()) {
            localProps.inputStream().use { props.load(it) }
        }

        buildConfigField("String", "BINANCE_WS_BASE", "\"wss://fstream.binance.com\"")
        buildConfigField("String", "BINANCE_REST_BASE", "\"https://fapi.binance.com\"")
        buildConfigField("String", "HYPERLIQUID_WS_BASE", "\"wss://api.hyperliquid.xyz/ws\"")
        buildConfigField("String", "HYPERLIQUID_REST_BASE", "\"https://api.hyperliquid.xyz\"")
        buildConfigField("String", "DYDX_REST_BASE", "\"https://indexer.dydx.trade/v4\"")
        buildConfigField("String", "DYDX_WS_BASE", "\"wss://indexer.dydx.trade/v4/ws\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "terminalpass"
            keyAlias = "trading_key"
            keyPassword = "terminalpass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

//     // externalNativeBuild {
//         // cmake {
//             // path = file("src/main/cpp/CMakeLists.txt")
//             // version = "3.22.1"
//         }
//     }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.navigation.compose)

    // Network
    implementation(libs.bundles.network)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.workmanager)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.biometric)

    // Web3j
    implementation(libs.web3j.core)

    // Kotlin extras
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // Sub-modules
    implementation(project(":core:data"))
    implementation(project(":core:engine"))
    implementation(project(":core:ui"))
    implementation(project(":feature:chart"))
    implementation(project(":feature:dom"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:watchlist"))

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
