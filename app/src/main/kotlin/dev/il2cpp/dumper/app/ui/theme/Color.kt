package dev.il2cpp.dumper.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * A hand-tuned "Il2Cpp green/teal + amber" palette used when dynamic color is unavailable
 * (Android < 12 or disabled). Generated from seed #00C2A8.
 */

private val primaryLight = Color(0xFF006A5F)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFF9FF2E3)
private val onPrimaryContainerLight = Color(0xFF00201C)
private val secondaryLight = Color(0xFF4A635E)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFCCE8E1)
private val onSecondaryContainerLight = Color(0xFF06201B)
private val tertiaryLight = Color(0xFF8A5100)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFFFDCBE)
private val onTertiaryContainerLight = Color(0xFF2C1600)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF410002)
private val backgroundLight = Color(0xFFF4FBF7)
private val onBackgroundLight = Color(0xFF161D1B)
private val surfaceLight = Color(0xFFF4FBF7)
private val onSurfaceLight = Color(0xFF161D1B)
private val surfaceVariantLight = Color(0xFFDAE5E1)
private val onSurfaceVariantLight = Color(0xFF3F4946)
private val outlineLight = Color(0xFF6F7976)
private val outlineVariantLight = Color(0xFFBEC9C5)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFEEF5F1)
private val surfaceContainerLight = Color(0xFFE8EFEB)
private val surfaceContainerHighLight = Color(0xFFE2E9E6)
private val surfaceContainerHighestLight = Color(0xFFDDE4E0)
private val inversePrimaryLight = Color(0xFF83D5C8)

private val primaryDark = Color(0xFF83D5C8)
private val onPrimaryDark = Color(0xFF003731)
private val primaryContainerDark = Color(0xFF005048)
private val onPrimaryContainerDark = Color(0xFF9FF2E3)
private val secondaryDark = Color(0xFFB0CCC5)
private val onSecondaryDark = Color(0xFF1C3530)
private val secondaryContainerDark = Color(0xFF324B46)
private val onSecondaryContainerDark = Color(0xFFCCE8E1)
private val tertiaryDark = Color(0xFFFFB871)
private val onTertiaryDark = Color(0xFF4A2800)
private val tertiaryContainerDark = Color(0xFF693C00)
private val onTertiaryContainerDark = Color(0xFFFFDCBE)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF0E1513)
private val onBackgroundDark = Color(0xFFDDE4E0)
private val surfaceDark = Color(0xFF0E1513)
private val onSurfaceDark = Color(0xFFDDE4E0)
private val surfaceVariantDark = Color(0xFF3F4946)
private val onSurfaceVariantDark = Color(0xFFBEC9C5)
private val outlineDark = Color(0xFF899390)
private val outlineVariantDark = Color(0xFF3F4946)
private val surfaceContainerLowestDark = Color(0xFF090F0E)
private val surfaceContainerLowDark = Color(0xFF161D1B)
private val surfaceContainerDark = Color(0xFF1A2120)
private val surfaceContainerHighDark = Color(0xFF242B2A)
private val surfaceContainerHighestDark = Color(0xFF2F3634)
private val inversePrimaryDark = Color(0xFF006A5F)

val LightColors: ColorScheme = lightColorScheme(
    primary = primaryLight, onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight, onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight, onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight, onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight, onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight, onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight, onError = onErrorLight,
    errorContainer = errorContainerLight, onErrorContainer = onErrorContainerLight,
    background = backgroundLight, onBackground = onBackgroundLight,
    surface = surfaceLight, onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight, onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight, outlineVariant = outlineVariantLight,
    inversePrimary = inversePrimaryLight,
    surfaceContainerLowest = surfaceContainerLowestLight, surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight, surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = primaryDark, onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark, onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark, onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark, onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark, onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark, onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark, onError = onErrorDark,
    errorContainer = errorContainerDark, onErrorContainer = onErrorContainerDark,
    background = backgroundDark, onBackground = onBackgroundDark,
    surface = surfaceDark, onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark, onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark, outlineVariant = outlineVariantDark,
    inversePrimary = inversePrimaryDark,
    surfaceContainerLowest = surfaceContainerLowestDark, surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark, surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

/** Accent colors for the per-metadata-version chips in the UI. */
object VersionAccents {
    fun forVersion(v: String): Color = when (v.substringBefore('.')) {
        "16", "17", "18", "19" -> Color(0xFF7E57C2)
        "20", "21", "22", "23" -> Color(0xFF42A5F5)
        "24" -> Color(0xFF26A69A)
        "27" -> Color(0xFF66BB6A)
        "29" -> Color(0xFFFFA726)
        "31" -> Color(0xFFEF5350)
        else -> Color(0xFF9E9E9E)
    }
}
