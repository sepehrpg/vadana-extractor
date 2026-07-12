package ir.vadana.extractor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF506078),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E2F8),
    onSecondaryContainer = Color(0xFF0D1B31),
    tertiary = Color(0xFF006C52),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9DF2D2),
    onTertiaryContainer = Color(0xFF002117),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF737780),
    outlineVariant = Color(0xFFC3C6D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBCC6DC),
    onSecondary = Color(0xFF243044),
    secondaryContainer = Color(0xFF3A465C),
    onSecondaryContainer = Color(0xFFD8E2F8),
    tertiary = Color(0xFF81D5B7),
    onTertiary = Color(0xFF003829),
    tertiaryContainer = Color(0xFF00513D),
    onTertiaryContainer = Color(0xFF9DF2D2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC3C6D0),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF44474F),
)

private val VadanaTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun VadanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VadanaTypography,
        content = content,
    )
}
