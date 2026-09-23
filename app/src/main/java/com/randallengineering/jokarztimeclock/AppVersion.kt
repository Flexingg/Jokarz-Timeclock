package com.randallengineering.jokarztimeclock

/**
 * The running build, read from the generated [BuildConfig] so it can never drift from
 * `versionName` / `versionCode` in `app/build.gradle.kts`.
 */
object AppVersion {
    /** e.g. "Jokarz Timeclock v2.8.0 (build 12)" — shown in the app header, Settings and diagnostics. */
    val label: String = "Jokarz Timeclock v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

    /** e.g. "Jokarz Timeclock v2.8.0" — the notification sub-text, so a screenshot proves the build. */
    val short: String = "Jokarz Timeclock v${BuildConfig.VERSION_NAME}"
}
