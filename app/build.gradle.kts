plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val privateRepositoryB64 = System.getenv("DADA_PRIVATE_REPOSITORY_B64").orEmpty()
val privateTokenB64 = System.getenv("DADA_PRIVATE_TOKEN_B64").orEmpty()
val stableDebugStorePath = System.getenv("DIGIAUTOSTART_SIGNING_STORE_FILE").orEmpty()
val stableDebugStorePassword = System.getenv("DIGIAUTOSTART_SIGNING_STORE_PASSWORD").orEmpty()

android {
    namespace = "pt.catharsys.digiautostart"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.catharsys.digiautostart"
        minSdk = 28
        targetSdk = 35
        versionCode = 32
        versionName = "0.6.21"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "androidCompatibility"

    productFlavors {
        create("android12") {
            dimension = "androidCompatibility"
            minSdk = 31
        }
        create("android9") {
            dimension = "androidCompatibility"
            minSdk = 28
            applicationIdSuffix = ".android9"
            versionNameSuffix = "-android9"
        }
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
            buildConfigField("String", "DADA_PRIVATE_REPOSITORY_B64", "\"$privateRepositoryB64\"")
            buildConfigField("String", "DADA_PRIVATE_TOKEN_B64", "\"$privateTokenB64\"")
        }
        getByName("release") {
            signingConfig = if (stableDebugStorePath.isNotBlank()) signingConfigs.getByName("stableDebug") else signingConfigs.getByName("debug")
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
    debugImplementation("org.smali:dexlib2:2.5.2")
}
