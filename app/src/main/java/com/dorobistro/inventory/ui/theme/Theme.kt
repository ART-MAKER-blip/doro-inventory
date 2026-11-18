package com.dorobistro.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "D'oro" = golden: a warm gold primary with a terracotta accent for low-stock warnings.
private val Light = lightColorScheme(
    primary = Color(0xFF7A5900), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA6), onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6B5D3F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E0BB), onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF9A4521), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCD), onTertiaryContainer = Color(0xFF360F00),
    background = Color(0xFFFFF8F2), onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F2), onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEDE1CF), onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667), outlineVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF2E7),
    surfaceContainer = Color(0xFFF5EDE1),
    surfaceContainerHigh = Color(0xFFF0E7DC),
    surfaceContainerHighest = Color(0xFFEAE1D6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2BF48), onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4300), onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD8C4A0), onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF52452A), onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFFFFB596), onTertiary = Color(0xFF581E00),
    tertiaryContainer = Color(0xFF7B2E0B), onTertiaryContainer = Color(0xFFFFDBCD),
    background = Color(0xFF17130B), onBackground = Color(0xFFEBE1D4),
    surface = Color(0xFF17130B), onSurface = Color(0xFFEBE1D4),
    surfaceVariant = Color(0xFF4D4639), onSurfaceVariant = Color(0xFFD0C5B4),
    outline = Color(0xFF999080), outlineVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFF120E07),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17),
    surfaceContainerHigh = Color(0xFF2E2921),
    surfaceContainerHighest = Color(0xFF39342B),
)

@Composable
fun DoroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
