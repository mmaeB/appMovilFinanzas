package com.upn3.proyecto_finanzas_personales.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = SurfaceContainerLowest,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurface,
    background = SurfaceContainerLowest,
    onBackground = OnSurface,
    outline = OutlineVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainer,
    surfaceContainerHighest = SurfaceBright,
)

@Composable
fun Proyecto_Finanzas_PersonalesTheme(
    darkTheme: Boolean = true, // Force dark theme for "The Sovereign Vault"
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
