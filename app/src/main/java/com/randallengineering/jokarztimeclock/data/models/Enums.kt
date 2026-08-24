package com.randallengineering.jokarztimeclock.data.models

enum class PayMode(val label: String) {
    GROSS("Gross"),
    NET("Take Home")
}

enum class PaySchedule(val label: String) {
    SEMI_MONTHLY("Semi-Monthly (1st-15th & 16th-EOM)"),
    BI_WEEKLY("Bi-Weekly (Every 2 Weeks)"),
    WEEKLY("Weekly (Monday - Sunday)"),
    MONTHLY("Monthly (1st - Last Day)")
}

enum class ThemeMode(val label: String) {
    DYNAMIC("Material You (Dynamic Wallpaper)"),
    DARK("Slate Dark"),
    AMOLED("True AMOLED Black"),
    EMERALD("Cyber Emerald"),
    AMBER("Amber Glow"),
    LIGHT("Material Light")
}

enum class PtoType {
    PTO,
    HOLIDAY,
    SICK
}
