import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. There is exactly ONE key for this app and it is the same key locally and in CI,
// so an update always installs straight over the previous build.
//
// Resolution order per value: environment first (CI: KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS /
// KEY_PASSWORD), then the gitignored keystore.properties (local builds). Neither the keystore nor
// its password is ever committed.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile: String? = signingValue("KEYSTORE_PATH", "storeFile")
val releaseStorePassword: String? = signingValue("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias: String? = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword: String? = signingValue("KEY_PASSWORD", "keyPassword")

/** Absolute path to the canonical release keystore, or null when it has not been configured. */
val releaseKeystoreFile: File? = releaseStoreFile?.let { path ->
    val f = File(path)
    if (f.isAbsolute) f else rootProject.file(path)
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
        versionCode = 16
        versionName = "2.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Nullable on purpose. If the key is missing AGP fails the *release* build (see the
            // guard below) instead of silently writing an unsigned or debug-signed APK - which is
            // exactly how the v2.8.2 CI artifact came out debug-signed.
            storeFile = releaseKeystoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            // All three schemes, explicitly: v1 for old installers, v2 (what the phone reported on),
            // v3 so the signing key can be rotated in the future without another uninstall.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Never the debug key: the release variant is only ever signed with the canonical key.
            signingConfig = signingConfigs.getByName("release")
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

// Fail closed. A release APK that is unsigned, partially signed, or signed with anything other than
// the canonical key is a bug that must stop the build - v2.8.2 shipped a debug-signed CI artifact
// because the release variant silently fell back to the debug key. There is no fallback any more.
tasks.matching { it.name == "packageRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        val ks = releaseKeystoreFile
        require(ks != null && ks.exists()) {
            "Release signing key not configured - refusing to build a release APK.\n" +
                "  resolved KEYSTORE_PATH: ${releaseStoreFile ?: "<unset>"}\n" +
                "Set KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD (CI) or create the gitignored\n" +
                "keystore.properties (local). See README > Release signing."
        }
    }
}
