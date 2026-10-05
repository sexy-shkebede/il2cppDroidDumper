package dev.il2cpp.dumper.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Monospace style for code (dump.cs, il2cpp.h, offsets). */
val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.sp,
)

private val default = Typography()

/** Slightly rounder, friendlier headings in the spirit of the expressive type scale. */
val ExpressiveTypography = Typography(
    displayLarge = default.displayLarge.copy(fontWeight = FontWeight.SemiBold),
    displayMedium = default.displayMedium.copy(fontWeight = FontWeight.SemiBold),
    displaySmall = default.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = default.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = default.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Expressive shapes trend larger and rounder. */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)
