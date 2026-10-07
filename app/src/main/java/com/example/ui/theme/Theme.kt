package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonTeal,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Purple80,
    onSecondary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4F378B),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = AccentGreen,
    onTertiary = Color(0xFF003919),
    background = DarkSurface,
    onBackground = Color(0xFFE2E2E6),
    surface = DarkSurfaceVariant,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF282C3D),
    onSurfaceVariant = Color(0xFFC3C7D4),
    outline = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Purple40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF23005C),
    tertiary = Color(0xFF006D39),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF191C1D),
    surface = Color.White,
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFE0E3E5),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFFC4C7C5)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
