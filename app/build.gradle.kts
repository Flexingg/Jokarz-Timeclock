import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. Kept identical for every build so the owner can install over the previous APK.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.randallengineering.jokarztimeclock"
    // 36 is required to compile Android 16's promoted-ongoing / Live Update APIs:
    // Notification.ProgressStyle, setShortCriticalText, hasPromotableCharacteristics()
    // and NotificationManager.canPostPromotedNotifications().
    compileSdk = 36

    defaultConfig {
        applicationId = "com.randallengineering.jokarztimeclock"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "2.8.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty() && keystoreProps.getProperty("storeFile") != null) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Falls back to the debug key when keystore.properties is absent (CI / fresh clone).
            signingConfig = if (keystoreProps.isNotEmpty() && keystoreProps.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // The Material 3 Expressive API (MaterialShapes, wavy progress, MotionScheme) is opt-in.
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
    }
    buildFeatures {
        compose = true
        // AppVersion reads BuildConfig.VERSION_NAME / VERSION_CODE so the on-screen build never goes stale.
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // 1.17.0 brings NotificationCompat.ProgressStyle and setRequestPromotedOngoing() (Live Updates).
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Pinned ahead of the BOM (the explicit version wins): 1.5.0-alpha10 is the first cached artifact
    // with the Material 3 Expressive API the hero uses (MaterialShapes, CircularWavyProgressIndicator,
    // MotionScheme, MaterialExpressiveTheme). 1.4.0 has no MaterialShapes / wavy indicator.
    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
