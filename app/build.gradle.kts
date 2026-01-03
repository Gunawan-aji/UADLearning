import org.gradle.kotlin.dsl.implementation

plugins {

    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.android)

    alias(libs.plugins.google.gms.google.services)

}



android {

    namespace = "com.uad.uadlearningapp"

    compileSdk {

        version = release(36)

    }



    defaultConfig {

        applicationId = "com.uad.uadlearningapp"

        minSdk = 24

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

        sourceCompatibility = JavaVersion.VERSION_11

        targetCompatibility = JavaVersion.VERSION_11

    }

    kotlinOptions {

        jvmTarget = "11"

    }

    buildFeatures {

        viewBinding = true

    }

}



dependencies {
    // UI & Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.google.android.material:material:1.11.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Firebase (Menggunakan BoM versi 32.7.0 sesuai kode kamu)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    // --- BARIS INI YANG HILANG DAN HARUS DITAMBAHKAN ---
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation(libs.firebase.database)
    implementation(libs.play.services.cast.tv)
    // --------------------------------------------------



    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.cloudinary:cloudinary-android:2.3.0")
}