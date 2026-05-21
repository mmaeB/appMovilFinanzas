package com.upn3.proyecto_finanzas_personales.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

enum class AppTheme {
    DEFAULT, OCEAN, GOLD, PURPLE, ROSE, LIGHT
}

fun getColorScheme(theme: AppTheme) = when (theme) {
    AppTheme.LIGHT -> lightColorScheme(
        primary = LightPrimary,
        onPrimary = Color.White,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = Color(0xFF00210B),
        surface = LightSurface,
        onSurface = LightOnSurface,
        background = LightSurface,
        onBackground = LightOnSurface,
        surfaceVariant = LightSurfaceContainer,
        onSurfaceVariant = Color(0xFF424940)
    )
    AppTheme.DEFAULT -> darkColorScheme(
        primary = Primary,
        onPrimary = SurfaceContainerLowest,
        primaryContainer = PrimaryContainer,
        surface = Surface,
        background = SurfaceContainerLowest,
        onSurface = OnSurface,
        surfaceVariant = SurfaceContainer
    )
    AppTheme.OCEAN -> darkColorScheme(
        primary = OceanPrimary,
        onPrimary = Color.White,
        primaryContainer = OceanPrimaryContainer,
        surface = OceanSurface,
        background = Color(0xFF000814),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF001D3D)
    )
    AppTheme.GOLD -> darkColorScheme(
        primary = GoldPrimary,
        onPrimary = Color.Black,
        primaryContainer = GoldPrimaryContainer,
        surface = GoldSurface,
        background = Color(0xFF000814),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF001D3D)
    )
    AppTheme.PURPLE -> darkColorScheme(
        primary = PurplePrimary,
        onPrimary = Color.Black,
        primaryContainer = PurplePrimaryContainer,
        surface = PurpleSurface,
        background = Color(0xFF121212),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E1E1E)
    )
    AppTheme.ROSE -> darkColorScheme(
        primary = RosePrimary,
        onPrimary = Color.White,
        primaryContainer = RosePrimaryContainer,
        surface = RoseSurface,
        background = Color(0xFF1A0A0E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2D1419)
    )
}

@Composable
fun Proyecto_Finanzas_PersonalesTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(theme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = 
                theme == AppTheme.LIGHT
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
