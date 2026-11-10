package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XomeDarkColorScheme = darkColorScheme(
    primary = VaultCyanPrimary,
    onPrimary = Color(0xFF001F26),
    primaryContainer = Color(0xFF082E38),
    onPrimaryContainer = Color(0xFFB3F6FF),
    secondary = CalcAccentOrange,
    onSecondary = Color(0xFF280C00),
    secondaryContainer = Color(0xFF3E1908),
    onSecondaryContainer = Color(0xFFFFDBCF),
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
