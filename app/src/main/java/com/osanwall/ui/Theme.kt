package com.osanwall.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark palette - Violet/Cyan nebula
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB6A0FF),
    onPrimary = Color(0xFF340090),
    primaryContainer = Color(0xFFA98FFF),
    onPrimaryContainer = Color(0xFF280072),
    secondary = Color(0xFF00E3FD),
    onSecondary = Color(0xFF004D57),
    secondaryContainer = Color(0xFF006875),
    onSecondaryContainer = Color(0xFFE8FBFF),
    tertiary = Color(0xFFFF6C95),
    onTertiary = Color(0xFF48001C),
    tertiaryContainer = Color(0xFFFD3E80),
    onTertiaryContainer = Color(0xFF100003),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFECEDF6),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFECEDF6),
    surfaceVariant = Color(0xFF22262F),
    onSurfaceVariant = Color(0xFFA9ABB3),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1C2028),
    surfaceContainerHighest = Color(0xFF22262F),
    surfaceContainerLow = Color(0xFF10131A),
    surfaceContainerLowest = Color(0xFF000000),
    outline = Color(0xFF73757D),
    outlineVariant = Color(0xFF45484F),
    error = Color(0xFFFF6E84),
    onError = Color(0xFF490013),
    errorContainer = Color(0xFFA70138),
    onErrorContainer = Color(0xFFFFB2B9),
    inverseSurface = Color(0xFFF9F9FF),
    inverseOnSurface = Color(0xFF52555C),
    inversePrimary = Color(0xFF6834EB),
    scrim = Color(0xFF000000)
)

// Light palette - clean violet/teal
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF652FE7),
    onPrimary = Color(0xFFF7F0FF),
    primaryContainer = Color(0xFFA98FFF),
    onPrimaryContainer = Color(0xFF280072),
    secondary = Color(0xFF006573),
    onSecondary = Color(0xFFDAF8FF),
    secondaryContainer = Color(0xFF54E3FC),
    onSecondaryContainer = Color(0xFF004F5A),
    tertiary = Color(0xFFB60051),
    onTertiary = Color(0xFFFFEFF0),
    tertiaryContainer = Color(0xFFFF8FA9),
    onTertiaryContainer = Color(0xFF66002B),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF2C2F30),
    surface = Color(0xFFF5F6F7),
    onSurface = Color(0xFF2C2F30),
    surfaceVariant = Color(0xFFDADDDF),
    onSurfaceVariant = Color(0xFF595C5D),
    surfaceContainer = Color(0xFFE6E8EA),
    surfaceContainerHigh = Color(0xFFE0E3E4),
    surfaceContainerHighest = Color(0xFFDADDDF),
    surfaceContainerLow = Color(0xFFEFF1F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFF757778),
    outlineVariant = Color(0xFFABADAE),
    error = Color(0xFFB41340),
    onError = Color(0xFFFFEFEF),
    errorContainer = Color(0xFFF74B6D),
    onErrorContainer = Color(0xFF510017),
    inverseSurface = Color(0xFF0C0F10),
    inverseOnSurface = Color(0xFF9B9D9E),
    inversePrimary = Color(0xFF9A7BFF),
    scrim = Color(0xFF000000)
)

@Composable
fun OsanWallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OsanWallTypography,
        content = content
    )
}
