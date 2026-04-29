package com.zhoulesin.zutils.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = RaycastBlue,
    onPrimary = RaycastWhite,
    secondary = RaycastMediumGray,
    tertiary = RaycastGreen,
    error = RaycastRed,
    background = RaycastDeepBackground,
    surface = RaycastSurface100,
    surfaceVariant = RaycastCardSurface,
    onSurface = RaycastNearWhite,
    onSurfaceVariant = RaycastMediumGray,
    outline = RaycastBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = RaycastBlue,
    onPrimary = RaycastWhite,
    secondary = RaycastMediumGray,
    tertiary = RaycastGreen,
    error = RaycastRed,
    background = RaycastDeepBackground,
    surface = RaycastSurface100,
    surfaceVariant = RaycastCardSurface,
    onSurface = RaycastNearWhite,
    onSurfaceVariant = RaycastMediumGray,
    outline = RaycastBorder,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun ZUtilsTheme(
    darkTheme: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}