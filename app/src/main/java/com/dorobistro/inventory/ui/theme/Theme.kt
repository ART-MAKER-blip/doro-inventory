package com.dorobistro.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Professional palette: deep navy for structure and primary actions, cool slate neutrals for
 * surfaces and text, and a restrained gold accent that keeps the "D'oro" (golden) identity.
 * Status colours (green / amber / red / blue) are used only to signal stock health.
 */

private val Light = lightColorScheme(
    primary = Color(0xFF1B3A5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F2), onPrimaryContainer = Color(0xFF0B2239),
    secondary = Color(0xFF9A7A1E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6ECCD), onSecondaryContainer = Color(0xFF3A2D05),
    tertiary = Color(0xFF0F6E6E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3EFEC), onTertiaryContainer = Color(0xFF032B2B),
    error = Color(0xFFB42318), onError = Color.White,
    errorContainer = Color(0xFFFEE4E2), onErrorContainer = Color(0xFF7A271A),
    background = Color(0xFFF3F5F8), onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEEF1F5), onSurfaceVariant = Color(0xFF526075),
    surfaceTint = Color(0xFF1B3A5C),
    outline = Color(0xFF98A2B3), outlineVariant = Color(0xFFE2E6EC),
    inverseSurface = Color(0xFF1D2939), inverseOnSurface = Color(0xFFF2F4F7), inversePrimary = Color(0xFFA9C7EA),
    surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFE4E8EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFF3F5F8),
    surfaceContainerHigh = Color(0xFFECEFF3),
    surfaceContainerHighest = Color(0xFFE4E8EE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7EA), onPrimary = Color(0xFF0B2239),
    primaryContainer = Color(0xFF233F60), onPrimaryContainer = Color(0xFFDCE6F2),
    secondary = Color(0xFFE2C46E), onSecondary = Color(0xFF3A2D05),
    secondaryContainer = Color(0xFF4A3B10), onSecondaryContainer = Color(0xFFF6ECCD),
    tertiary = Color(0xFF7FD3CB), onTertiary = Color(0xFF032B2B),
    tertiaryContainer = Color(0xFF0E4A4A), onTertiaryContainer = Color(0xFFD3EFEC),
    error = Color(0xFFF97066), onError = Color(0xFF55160C),
    errorContainer = Color(0xFF5A1D17), onErrorContainer = Color(0xFFFEE4E2),
    background = Color(0xFF0D131C), onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF141C27), onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1F2936), onSurfaceVariant = Color(0xFFA4B0C0),
    surfaceTint = Color(0xFFA9C7EA),
    outline = Color(0xFF5D6B7E), outlineVariant = Color(0xFF263242),
    inverseSurface = Color(0xFFE6EAF0), inverseOnSurface = Color(0xFF141C27), inversePrimary = Color(0xFF1B3A5C),
    surfaceBright = Color(0xFF253041), surfaceDim = Color(0xFF0D131C),
    surfaceContainerLowest = Color(0xFF0A1018),
    surfaceContainerLow = Color(0xFF111924),
    surfaceContainer = Color(0xFF17202C),
    surfaceContainerHigh = Color(0xFF1D2733),
    surfaceContainerHighest = Color(0xFF26313F),
)

/** Colours outside Material's scheme: stock-health status and the navigation sidebar. */
@Immutable
data class StatusColors(
    val success: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val danger: Color, val dangerContainer: Color, val onDangerContainer: Color,
    val info: Color, val infoContainer: Color, val onInfoContainer: Color,
    val neutral: Color, val neutralContainer: Color, val onNeutralContainer: Color,
    val sidebar: Color, val onSidebar: Color, val onSidebarMuted: Color, val sidebarSelected: Color, val accent: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF067647), successContainer = Color(0xFFDCFAE6), onSuccessContainer = Color(0xFF05603A),
    warning = Color(0xFFB54708), warningContainer = Color(0xFFFEF0C7), onWarningContainer = Color(0xFF93370D),
    danger = Color(0xFFB42318), dangerContainer = Color(0xFFFEE4E2), onDangerContainer = Color(0xFF912018),
    info = Color(0xFF1F5FA8), infoContainer = Color(0xFFE0ECFA), onInfoContainer = Color(0xFF173E6E),
    neutral = Color(0xFF667085), neutralContainer = Color(0xFFEEF1F5), onNeutralContainer = Color(0xFF344054),
    sidebar = Color(0xFF0F2238), onSidebar = Color(0xFFFFFFFF), onSidebarMuted = Color(0xFF9FB1C7),
    sidebarSelected = Color(0xFF1E3A5A), accent = Color(0xFFD4AF37),
)

private val DarkStatus = StatusColors(
    success = Color(0xFF47CD89), successContainer = Color(0xFF0E3B26), onSuccessContainer = Color(0xFFA6F4C5),
    warning = Color(0xFFFDB022), warningContainer = Color(0xFF4A2B07), onWarningContainer = Color(0xFFFEDF89),
    danger = Color(0xFFF97066), dangerContainer = Color(0xFF55160C), onDangerContainer = Color(0xFFFECDCA),
    info = Color(0xFF84ADFF), infoContainer = Color(0xFF16325A), onInfoContainer = Color(0xFFD1E0FF),
    neutral = Color(0xFF98A2B3), neutralContainer = Color(0xFF26313F), onNeutralContainer = Color(0xFFD0D5DD),
    sidebar = Color(0xFF09121D), onSidebar = Color(0xFFFFFFFF), onSidebarMuted = Color(0xFF8B9BB0),
    sidebarSelected = Color(0xFF1A2B40), accent = Color(0xFFE2C46E),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Access with `MaterialTheme.status.success` etc. */
val MaterialTheme.status: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current

private val base = Typography()
private val AppTypography = Typography(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DoroTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
