plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.catharsys.digiautostart"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.catharsys.digiautostart"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-debug-diagnostics"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
