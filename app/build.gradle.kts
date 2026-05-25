plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Removed: id("com.google.devtools.ksp")  -- was only needed for Room's code generation
    // Removed: id("androidx.room")             -- Room Gradle plugin no longer needed
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.pulserds_prog7313"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pulserds_prog7313"
        minSdk = 26
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    // Removed: room { schemaDirectory(...) } -- Room schema exports no longer needed
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation(libs.androidx.activity)

    // Firebase BoM -- manages all Firebase library versions automatically
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))

    // Firebase Authentication -- handles login, registration, and password reset
    implementation("com.google.firebase:firebase-auth")

    // Firebase Firestore -- replaces Room as the database layer
    implementation("com.google.firebase:firebase-firestore")

    // Firebase Analytics (was already present)
    implementation("com.google.firebase:firebase-analytics")

    // Removed: Room dependencies
    // implementation("androidx.room:room-ktx:$room")
    // ksp("androidx.room:room-compiler:$room")

    // ViewModel + Coroutines (keep as-is)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Coroutine support for Firebase Tasks (lets you use .await() on Firebase calls)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    //Credential Manager for Google Sign-In
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}