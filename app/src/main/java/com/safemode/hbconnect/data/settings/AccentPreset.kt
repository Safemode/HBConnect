package com.safemode.hbconnect.data.settings

/**
 * Accent colour choice, applied on top of the light/dark [ThemePreference]. [AUTO] uses the system
 * wallpaper colours (Material You) on Android 12+ and the Homebox palette otherwise; [HOMEBOX] is
 * the built-in teal palette; the colour presets re-tint the accent roles from their [seed]; [CUSTOM]
 * uses the user-picked colour stored in [ConnectionConfig.accentColor]. [seed] (ARGB) also serves as
 * the swatch colour shown in Settings.
 */
enum class AccentPreset(val label: String, val seed: Long?) {
    AUTO("Automatic", null),
    HOMEBOX("Homebox", 0xFF006A60),
    BLUE("Blue", 0xFF1565C0),
    INDIGO("Indigo", 0xFF3F51B5),
    VIOLET("Violet", 0xFF6750A4),
    PINK("Pink", 0xFFC2185B),
    RED("Red", 0xFFC62828),
    ORANGE("Orange", 0xFFE65100),
    GREEN("Green", 0xFF2E7D32),
    CUSTOM("Custom", null),
}
