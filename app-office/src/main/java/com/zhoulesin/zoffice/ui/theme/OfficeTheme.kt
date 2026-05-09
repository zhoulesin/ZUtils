package com.zhoulesin.zoffice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OfficeLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    tertiary = Color(0xFF00897B),
    background = Color(0xFFF0F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E8EF),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF73777F),
)

private val OfficeDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    secondary = Color(0xFFFFB74D),
    background = Color(0xFF121316),
    surface = Color(0xFF1E2128),
    onSurface = Color(0xFFE3E2E6),
)

@Composable
fun OfficeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) OfficeDark else OfficeLight
    MaterialTheme(
        colorScheme = scheme,
        content = {
            Surface(color = scheme.background) {
                content()
            }
        },
    )
}
