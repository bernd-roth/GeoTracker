import org.gradle.api.JavaVersion.VERSION_11
import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    // Replace KAPT with KSP
    // id("kotlin-kapt") // Remove this line
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" // Add this line
}

android {
    namespace = "at.co.netconsulting.geotracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "at.co.netconsulting.geotracker"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val outputImpl = output as com.android.build.api.variant.impl.VariantOutputImpl
                outputImpl.outputFileName.set("geotracker.apk")
            }
        }
    }

    compileOptions {
        sourceCompatibility = VERSION_11
        targetCompatibility = VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // Dependencies remain the same...
    // Core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Room dependencies
    implementation("androidx.room:room-ktx:2.6.1")
    implementation(libs.play.services.maps)
    implementation(libs.firebase.database.ktx)
    implementation(libs.androidx.navigation.compose)
//    kapt("androidx.room:room-compiler:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Location services
    implementation(libs.play.services.location)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debugging tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Additional dependencies
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.30.1")
    implementation("org.greenrobot:eventbus:3.3.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.10")
    testImplementation("org.mockito:mockito-core:4.2.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("joda-time:joda-time:2.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    // RxAndroidBLE for Bluetooth LE communication
    implementation("com.polidea.rxandroidble3:rxandroidble:1.17.2")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // restoration logic
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}