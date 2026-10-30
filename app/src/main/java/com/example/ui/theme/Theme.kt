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
        MangoAppearance.light = prefs.getBoolean(MangoAppearance.KEY, false)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MangoAppearance.KEY || key == null) MangoAppearance.light = prefs.getBoolean(MangoAppearance.KEY, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val light = MangoAppearance.light
    val base = if (light) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = MangoYellow, onPrimary = if (light) Color.White else MangoButtonInk,
        primaryContainer = MutedSurface2, onPrimaryContainer = Cream,
        secondary = JuicyOrange, onSecondary = MangoButtonInk,
        secondaryContainer = MutedSurface2, onSecondaryContainer = Cream,
        tertiary = LeafGreen, onTertiary = if (light) Color.White else MangoButtonInk,
        background = DarkBackground, onBackground = Cream,
        surface = MutedSurface1, onSurface = Cream,
        surfaceVariant = MutedSurface2, onSurfaceVariant = GoldAccent,
        surfaceTint = LeafGreen, surfaceContainer = DarkBackground, surfaceContainerHigh = MutedSurface2,
        surfaceContainerHighest = MutedSurface2, surfaceContainerLow = MutedSurface1,
        outline = WarmBrown, error = if (light) Color(0xFFB3261E) else Color(0xFFFF8C95))
    SideEffect {
        context.findActivity()?.let { activity ->
            val barStyle = if (light) SystemBarStyle.light(0xFFFFF8E7.toInt(), 0xFFFFF8E7.toInt())
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
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)), content = content)
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
