import org.gradle.api.JavaVersion.VERSION_11
import org.gradle.api.JavaVersion.VERSION_1_8

// Helper function to auto-increment version
fun getNextVersion(currentVersion: String): String {
    val versionPattern = Regex("""(\d+)\.(\d+)""")
    val matchResult = versionPattern.find(currentVersion)
    
    return if (matchResult != null) {
        val major = matchResult.groupValues[1].toInt()
        val minor = matchResult.groupValues[2].toInt()
        "$major.${minor + 1}"
    } else {
        "3.5" // Fallback version
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    // id("kotlin-kapt")
    id("com.google.devtools.ksp") version "1.9.0-1.0.13"
}

android {
    namespace = "at.co.netconsulting.geotracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.co.netconsulting.geotracker"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "${getNextVersion("7.11")} (16-01-2026: show weather conditions in weather forecast, including temperature, humidity, wind speed plus direction, and snow)"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // AppAuth redirect scheme for OAuth
        manifestPlaceholders["appAuthRedirectScheme"] = "http"
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
        // Exclude server-side websocket files from the APK
        resources.excludes.add("**/websocket/**")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core AndroidX dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Compose BOM and dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle and ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // Room dependencies
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Location services
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    // OSMDroid for maps
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Network dependencies - consolidated versions
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // OAuth and authentication
    implementation("net.openid:appauth:0.11.1")
    implementation("androidx.browser:browser:1.7.0")

    // EventBus
    implementation("org.greenrobot:eventbus:3.3.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Date/Time
    implementation("joda-time:joda-time:2.12.5")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // System UI Controller
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // RxAndroidBLE for Bluetooth LE communication
    implementation("com.polidea.rxandroidble3:rxandroidble:1.17.2")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")

    // Firebase (if needed)
    implementation(libs.firebase.database.ktx)

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debugging tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}