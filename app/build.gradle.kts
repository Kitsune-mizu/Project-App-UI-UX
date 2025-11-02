plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.alpha"
    compileSdk = 36 // Target Android 16 (currently Android U/UpsideDownCake or latest available)

    defaultConfig {
        applicationId = "com.android.alpha"
        minSdk = 24 // Android 7.0 (Nougat)
        targetSdk = 36 // Target the latest SDK (36)
        versionCode = 1
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Disabling obfuscation for now
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true // Enables View Binding for easier view access
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson.v2101)
    implementation(libs.androidx.preference.ktx)

    // --- Core Android & Kotlin Dependencies ---
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // --- Lifecycle & Navigation ---
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.activity)

    // --- Third-Party / Feature Dependencies ---
    implementation(libs.lottie) // Lottie Animation (Duplicated but harmless)
    implementation(libs.glide) // Image Loading
    implementation(libs.gson) // JSON serialization/deserialization
    implementation(libs.osmdroid.android) // OpenStreetMap Android Library
    implementation(libs.osmdroid.wms) // OpenStreetMap WMS extension
    implementation(libs.okhttp) // HTTP client (for map data/geocoding)
    implementation(libs.play.services.location.v2101) // Google Location Services (FusedLocationProviderClient)
    implementation(libs.shimmer) // Shimmer Effect (Loading placeholder)

    // --- UI/Utility ---
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.swiperefreshlayout)

    // --- Map (Looks like an unused or placeholder Google Maps dependency) ---
    implementation(libs.play.services.maps)

    // --- Annotation Processor ---
    annotationProcessor(libs.compiler) // Likely for Glide or other libraries

    // --- Testing Dependencies ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}