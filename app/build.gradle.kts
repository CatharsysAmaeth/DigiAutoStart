plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val diagnosticsTokenB64 = System.getenv("DIAGNOSTICS_REPO_TOKEN_B64").orEmpty()
val diagnosticsPasswordB64 = System.getenv("DIAGNOSTICS_ZIP_PASSWORD_B64").orEmpty()
val stableDebugStorePath = System.getenv("DIGIAUTOSTART_SIGNING_STORE_FILE").orEmpty()
val stableDebugStorePassword = System.getenv("DIGIAUTOSTART_SIGNING_STORE_PASSWORD").orEmpty()

android {
    namespace = "pt.catharsys.digiautostart"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.catharsys.digiautostart"
        minSdk = 31
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (stableDebugStorePath.isNotBlank()) {
            create("stableDebug") {
                storeFile = file(stableDebugStorePath)
                storePassword = stableDebugStorePassword
                keyAlias = "digiautostart"
                keyPassword = stableDebugStorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (stableDebugStorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
            buildConfigField("String", "DIAGNOSTICS_GITHUB_TOKEN_B64", "\"$diagnosticsTokenB64\"")
            buildConfigField("String", "DIAGNOSTICS_ZIP_PASSWORD_B64", "\"$diagnosticsPasswordB64\"")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.core:core:1.17.0")
    debugImplementation("net.lingala.zip4j:zip4j:2.11.5")
}
