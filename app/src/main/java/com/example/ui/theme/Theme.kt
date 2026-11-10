package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XomeDarkColorScheme = darkColorScheme(
    primary = VaultAccentPrimary,
    onPrimary = Color(0xFF231710),
    primaryContainer = Color(0xFF3E3025),
    onPrimaryContainer = Color(0xFFFFD8AF),
    secondary = CalcAccentMuted,
    onSecondary = Color(0xFF282019),
    secondaryContainer = Color(0xFF342C26),
    onSecondaryContainer = Color(0xFFEAD5C1),
    tertiary = VaultEmeraldSuccess,
    onTertiary = Color(0xFF002114),
    background = CalcChassisBg,
    onBackground = CalcTextPrimary,
    surface = VaultSurface,
    onSurface = CalcTextPrimary,
    surfaceVariant = VaultSurfaceElevated,
    onSurfaceVariant = CalcTextSecondary,
    outline = VaultBorderSubtle,
    error = VaultCrimsonDanger,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = XomeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
