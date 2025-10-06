plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // CRITICAL: Ensure this is the correct alias for the services plugin
    alias(libs.plugins.google.gms.google.services)

    // Kotlin Kapt is correctly used for annotation processing
    id ("kotlin-kapt")
}

android {
    namespace = "com.functions.goshop"
    // Use the latest supported SDK versions, typically 34/35 now
    compileSdk = 36

    defaultConfig {
        applicationId = "com.functions.goshop"
        minSdk = 24
        targetSdk = 36 // Match compileSdk for stability
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Use Java 17 or higher if your Android Studio supports it, or stick to 1.8 for max compatibility.
        // Given your current setup, 11 is acceptable but 17 is recommended if possible.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Core AndroidX Dependencies (already using libs, just ensuring order)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation("com.google.android.material:material:1.13.0") // Material library is essential

    // ----------------------------------------------------------------------
    // FIREBASE
    // ----------------------------------------------------------------------
    // Firebase Authentication & Firestore (Use BoM or specific versions)
    // NOTE: com.google.firebase:firebase-database is for Realtime Database. Check if needed.
    implementation("com.google.firebase:firebase-auth") // Use BoM for versioning if possible
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database") // Only if you use Realtime Database

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    // Facebook Login
    implementation("com.facebook.android:facebook-android-sdk:18.1.3")
    // GitHub OAuth via Firebase (Firebase UI)
    implementation("com.firebaseui:firebase-ui-auth:9.0.0")

    // ML Kit for Translation
    implementation("com.google.mlkit:translate:17.0.3")

    // ----------------------------------------------------------------------
    // ROOM (Local Database) - CRITICAL FIXES APPLIED
    // ----------------------------------------------------------------------
    val room_version = "2.7.0-alpha01"
    // Implementation of Room runtime
    implementation("androidx.room:room-runtime:$room_version")
    // Annotation Processor (kapt is correct)
    kapt("androidx.room:room-compiler:$room_version")
    // Coroutines support (recommended for async database operations)
    implementation("androidx.room:room-ktx:$room_version")

    // ----------------------------------------------------------------------
    // IMAGE LOADING (Glide) - CRITICAL FIXES APPLIED
    // ----------------------------------------------------------------------
    // Glide Implementation (Use the standard group/module names)
    implementation("com.github.bumptech.glide:glide:4.16.0") // Correct group, updated version
    // Glide Annotation Processor (must use `kapt` instead of `annotationProcessor` in a Kotlin module with kapt enabled)
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Circle ImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // ----------------------------------------------------------------------
    //  CREDENTIALS & TESTING (No changes needed, assuming libs definitions are correct)
    // ----------------------------------------------------------------------
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}