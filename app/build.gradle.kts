// build.gradle.kts (app module)

plugins {
    // Plugins should always be declared first
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)

    // Apply Kapt plugin for annotation processing (Room, Glide)
    // Note: KSP is the modern replacement for Kapt, but we'll stick to Kapt if dependencies demand it.
    id ("kotlin-kapt")
}

android {
    namespace = "com.functions.goshop"

    // Modern SDK targeting
    compileSdk = 36 // Latest stable API 35

    defaultConfig {
        applicationId = "com.functions.goshop"
        minSdk = 24
        targetSdk = 36 // Match compileSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Should be true for release
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        // Modern standard is Java 17, but 21 is becoming common.
        // Using Java 17 for robustness.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.generativeai)

    // ----------------------------------------------------------------------
    // TESTING DEPENDENCIES (The Consolidated Fix)
    // ----------------------------------------------------------------------

    // 1. Core JUnit Framework (Correct)
    testImplementation("junit:junit:4.13.2")

    // 2. Kotlin Test (Bridges JUnit with Kotlin standard library)
    // THIS is the line that must be scoped with 'testImplementation'
    testImplementation(kotlin("test"))

    // 3. AndroidX Extensions (Recommended)
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")

    // Optional: Mocking framework for local tests
    testImplementation("org.mockito:mockito-core:4.11.0")

    // For testing coroutines
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // ----------------------------------------------------------------------
    // INSTRUMENTED TESTS (Runs on emulator/device)
    // ----------------------------------------------------------------------
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")



    // Your existing dependencies remain here...
    implementation ("androidx.appcompat:appcompat:1.7.0")
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // Use .ktx for activity
    implementation(libs.androidx.constraintlayout)

    // UI/Material
     // Using the aliased name from the TOML


    // ----------------------------------------------------------------------
    // FIREBASE (Using the latest stable BoM)
    // ----------------------------------------------------------------------
    // The BoM (Bill of Materials) controls ALL Firebase versions consistently
    implementation(platform("com.google.firebase:firebase-bom:33.0.0")) // Updated BoM version

    // Firebase Core services
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    // Firebase UI (For Auth) - Use BoM or latest stable
    implementation("com.firebaseui:firebase-ui-auth:8.0.2") // Latest stable from FirebaseUI 8.x

    // Google Sign In (Should be managed by the Credentials API or just the GMS auth library)
    implementation("com.google.android.gms:play-services-auth:21.0.0")

    // Facebook Login
    implementation("com.facebook.android:facebook-login:17.0.0") // Updated module name and version

    // ML Kit for Translation (Using the latest stable)
    implementation("com.google.mlkit:translate:17.0.3")


    // Activity KTX
    implementation("androidx.activity:activity-ktx:1.9.0")

    // UI/Material
    implementation("com.google.android.material:material:1.12.0")
    // In app/build.gradle.kts:

    // ----------------------------------------------------------------------
    // ROOM (Local Database) - Requires TOML setup for consistency
    // ----------------------------------------------------------------------
    // Assuming you have 'androidx-room-runtime', 'androidx-room-ktx', and 'androidx-room-compiler' in your TOML
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:${roomVersion}")

    implementation(libs.androidx.room.ktx)
    kapt("androidx.room:room-compiler:2.6.1")// The compiler must be kapt/ksp

    // ----------------------------------------------------------------------
    // IMAGE LOADING (GLIDE)
    // ----------------------------------------------------------------------
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Helper Library - Circle Image View
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation(kotlin("test"))
}