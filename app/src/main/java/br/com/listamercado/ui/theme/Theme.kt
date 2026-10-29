package br.com.listamercado.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7A3D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA3F5B6),
    onPrimaryContainer = Color(0xFF00210B),
    secondaryContainer = Color(0xFFD4E8D5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D89B),
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF005225),
    onPrimaryContainer = Color(0xFFA3F5B6),
    secondaryContainer = Color(0xFF384B3B),
)

@Composable
fun ListaMercadoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        // Android 12+: usa as cores do papel de parede
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
