package com.aripd.radyola.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Web sürümüyle aynı palet — koyu lacivert zemin, indigo/mor vurgu.
val RadyolaIndigo = Color(0xFF818CF8)
val RadyolaPurple = Color(0xFFC084FC)
val RadyolaCyan = Color(0xFF06B6D4)
val RadyolaPink = Color(0xFFEC4899)

private val DarkBackground = Color(0xFF0A0A1A)
private val DarkSurface = Color(0xFF141428)
private val DarkOnSurface = Color(0xFFE4E4EF)
private val DarkMuted = Color(0xFF9191AB)

private val LightBackground = Color(0xFFF7F7FB)
private val LightSurface = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    primary = RadyolaIndigo,
    onPrimary = Color(0xFF0A0A1A),
    secondary = RadyolaPurple,
    tertiary = RadyolaCyan,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF1D1D38),
    onSurfaceVariant = DarkMuted,
    outline = Color(0xFF3A3A5C),
    error = Color(0xFFFF6B81)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B54D6),
    onPrimary = Color.White,
    secondary = Color(0xFF9333EA),
    tertiary = Color(0xFF0891B2),
    background = LightBackground,
    onBackground = Color(0xFF1A1A2E),
    surface = LightSurface,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFECECF5),
    onSurfaceVariant = Color(0xFF5B5B72),
    outline = Color(0xFFCFCFE0),
    error = Color(0xFFD32F4F)
)

/** Logo ve oynat düğmesinde kullanılan indigo → mor geçişi. */
val BrandGradient = Brush.linearGradient(listOf(RadyolaIndigo, RadyolaPurple))

private val RadyolaTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun RadyolaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalContext.current

    SideEffect {
        (view as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = RadyolaTypography,
        content = content
    )
}
