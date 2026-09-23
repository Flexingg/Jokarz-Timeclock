plugins {
    // 8.10.x: androidx.core 1.17.0 (Live Update APIs) requires AGP >= 8.9.1; 8.10 also supports compileSdk 36
    // and still runs on Gradle 8.11.1.
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
