package com.example.ui.theme

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

@Composable
fun MojaDietaAiTheme(darkTheme: Boolean = false, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember(context) { context.getSharedPreferences(MangoAppearance.PREFS, 0) }
    DisposableEffect(prefs) {
        MangoAppearance.light = prefs.getBoolean(MangoAppearance.KEY, true)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MangoAppearance.KEY || key == null) MangoAppearance.light = prefs.getBoolean(MangoAppearance.KEY, true)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val light = MangoAppearance.light
    val base = if (light) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = if (light) Color(0xFFB65E00) else MangoYellow, onPrimary = if (light) Color.White else MangoButtonInk,
        primaryContainer = if (light) Color(0xFFFFE5CE) else MutedSurface2, onPrimaryContainer = if (light) Color(0xFF5A3100) else Cream,
        secondary = LeafGreen, onSecondary = Color.White,
        secondaryContainer = if (light) Color(0xFFDFF7E9) else MutedSurface2, onSecondaryContainer = if (light) Color(0xFF174C32) else Cream,
        tertiary = MangoLavender, onTertiary = Color.White,
        tertiaryContainer = if (light) Color(0xFFEDE7FF) else Color(0xFF342A54), onTertiaryContainer = if (light) Color(0xFF3B2869) else Color.White,
        background = DarkBackground, onBackground = Cream,
        surface = MutedSurface1, onSurface = Cream,
        surfaceVariant = MutedSurface2, onSurfaceVariant = GoldAccent,
        surfaceTint = LeafGreen, surfaceContainer = DarkBackground, surfaceContainerHigh = MutedSurface2,
        surfaceContainerHighest = MutedSurface2, surfaceContainerLow = MutedSurface1,
        outline = WarmBrown, outlineVariant = if (light) Color(0xFFE8DED1) else Color(0xFF385343),
        inverseSurface = if (light) Color(0xFF243E30) else Color(0xFFF5F6F1),
        inverseOnSurface = if (light) Color.White else Color(0xFF243E30),
        error = if (light) Color(0xFFB3261E) else Color(0xFFFF8C95))
    SideEffect {
        context.findActivity()?.let { activity ->
            val barStyle = if (light) SystemBarStyle.light(0xFFFFFAF1.toInt(), 0xFFFFFAF1.toInt())
                else SystemBarStyle.dark(0xFF0B1017.toInt())
            activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    com.example.ui.components.MangoMotionProvider {
        MaterialTheme(colorScheme = scheme, typography = Typography,
            shapes = Shapes(extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)), content = content)
    }
}

@Composable
fun MyApplicationTheme(darkTheme: Boolean = false, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    MojaDietaAiTheme(darkTheme, dynamicColor, content)
}

private fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? ComponentActivity
}
