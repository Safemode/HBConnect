package com.safemode.hbconnect.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.safemode.hbconnect.data.settings.AccentPreset
import com.safemode.hbconnect.data.settings.ThemePreference

private val LightColors = lightColorScheme(
    primary = md_primary_light,
    onPrimary = md_onPrimary_light,
    primaryContainer = md_primaryContainer_light,
    onPrimaryContainer = md_onPrimaryContainer_light,
    secondary = md_secondary_light,
    onSecondary = md_onSecondary_light,
    secondaryContainer = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    tertiary = md_tertiary_light,
    onTertiary = md_onTertiary_light,
    tertiaryContainer = md_tertiaryContainer_light,
    onTertiaryContainer = md_onTertiaryContainer_light,
    error = md_error_light,
    onError = md_onError_light,
    background = md_background_light,
    onBackground = md_onBackground_light,
    surface = md_surface_light,
    onSurface = md_onSurface_light,
    surfaceVariant = md_surfaceVariant_light,
    onSurfaceVariant = md_onSurfaceVariant_light,
    outline = md_outline_light,
)

private val DarkColors = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = md_onPrimary_dark,
    primaryContainer = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    secondary = md_secondary_dark,
    onSecondary = md_onSecondary_dark,
    secondaryContainer = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary = md_tertiary_dark,
    onTertiary = md_onTertiary_dark,
    tertiaryContainer = md_tertiaryContainer_dark,
    onTertiaryContainer = md_onTertiaryContainer_dark,
    error = md_error_dark,
    onError = md_onError_dark,
    background = md_background_dark,
    onBackground = md_onBackground_dark,
    surface = md_surface_dark,
    onSurface = md_onSurface_dark,
    surfaceVariant = md_surfaceVariant_dark,
    onSurfaceVariant = md_onSurfaceVariant_dark,
    outline = md_outline_dark,
)

@Composable
fun HBConnectTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    accent: AccentPreset = AccentPreset.AUTO,
    accentColor: Int = 0xFF1565C0.toInt(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK, ThemePreference.MIDNIGHT -> true
    }

    val context = LocalContext.current
    // "Automatic" uses the wallpaper-based Material You colours on Android 12+, otherwise the brand
    // palette. Any other accent re-tints the brand palette's accent roles from its seed colour.
    val useDynamic = accent == AccentPreset.AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        useDynamic -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val seed: Color? = when (accent) {
        AccentPreset.AUTO, AccentPreset.HOMEBOX -> null
        AccentPreset.CUSTOM -> Color(accentColor)
        else -> accent.seed?.let { Color(it) }
    }
    val accented = if (seed != null) base.withAccent(seed, darkTheme) else base

    // Midnight keeps the accent colours but blacks out the large surface areas for OLED.
    val colorScheme = if (themePreference == ThemePreference.MIDNIGHT) accented.toMidnight() else accented

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HBTypography,
    ) {
        // Default content colour for any Text/Icon not inside a surface-colored component (Card,
        // Button, app bars set their own). Without this, bare screen text falls back to black and is
        // invisible on the Dark/Midnight themes.
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            content = content,
        )
    }
}

/**
 * Re-tints the accent roles (primary/secondary/tertiary and their on/container pairs) from [seed],
 * following Material 3's tonal role conventions, while leaving the neutral surfaces and error roles
 * from the base scheme intact. Tones are approximated with HSL lightness, which is close enough for
 * an accent-colour feature without pulling in a full colour-science dependency.
 */
private fun ColorScheme.withAccent(seed: Color, dark: Boolean): ColorScheme {
    val secondarySeed = seed.withSaturation(0.55f)
    val tertiarySeed = seed.withHueShift(60f)
    return if (dark) {
        copy(
            primary = seed.tone(80),
            onPrimary = seed.tone(20),
            primaryContainer = seed.tone(30),
            onPrimaryContainer = seed.tone(90),
            inversePrimary = seed.tone(40),
            secondary = secondarySeed.tone(80),
            onSecondary = secondarySeed.tone(20),
            secondaryContainer = secondarySeed.tone(30),
            onSecondaryContainer = secondarySeed.tone(90),
            tertiary = tertiarySeed.tone(80),
            onTertiary = tertiarySeed.tone(20),
            tertiaryContainer = tertiarySeed.tone(30),
            onTertiaryContainer = tertiarySeed.tone(90),
        )
    } else {
        copy(
            primary = seed.tone(40),
            onPrimary = Color.White,
            primaryContainer = seed.tone(90),
            onPrimaryContainer = seed.tone(10),
            inversePrimary = seed.tone(80),
            secondary = secondarySeed.tone(40),
            onSecondary = Color.White,
            secondaryContainer = secondarySeed.tone(90),
            onSecondaryContainer = secondarySeed.tone(10),
            tertiary = tertiarySeed.tone(40),
            onTertiary = Color.White,
            tertiaryContainer = tertiarySeed.tone(90),
            onTertiaryContainer = tertiarySeed.tone(10),
        )
    }
}

/** This colour at the given HSL lightness (0-100), keeping its hue and saturation. */
private fun Color.tone(lightness: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = (lightness / 100f).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.withHueShift(degrees: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[0] = ((hsl[0] + degrees) % 360f + 360f) % 360f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.withSaturation(factor: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * factor).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Pure black where a whole screen is filled, but the container tones stay very dark grey rather
 * than black, so cards, the drawer sheet and dividers remain visible against the page.
 */
private fun ColorScheme.toMidnight(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
)
