
package com.example.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val MangoCreamBackground = Color(0xFFFFFAF3)
private val MangoCard = Color(0xFFFFFFFF)
private val MangoSoftCream = Color(0xFFFFF0DC)
private val MangoSoftOrange = Color(0xFFFFE3BE)
private val MangoText = Color(0xFF30271F)
private val MangoSecondaryText = Color(0xFF695A4C)

@Composable
fun MojaDietaAiTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember(context) {
        context.getSharedPreferences(
            MangoAppearance.PREFS,
            Context.MODE_PRIVATE
        )
    }

    DisposableEffect(prefs) {
        MangoAppearance.light = prefs.getBoolean(MangoAppearance.KEY, true)

        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == MangoAppearance.KEY || key == null) {
                    MangoAppearance.light =
                        prefs.getBoolean(MangoAppearance.KEY, true)
                }
            }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // The stored appearance preference remains authoritative, as in the original file.
    val light = MangoAppearance.light

    val scheme = if (light) {
        lightColorScheme(
            primary = JuicyOrange,
            onPrimary = Color.White,
            primaryContainer = MangoSoftOrange,
            onPrimaryContainer = MangoText,
            secondary = LeafGreen,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE4F0DD),
            onSecondaryContainer = MangoText,
            tertiary = MangoYellow,
            onTertiary = MangoText,
            background = MangoCreamBackground,
            onBackground = MangoText,
            surface = MangoCard,
            onSurface = MangoText,
            surfaceVariant = MangoSoftCream,
            onSurfaceVariant = MangoSecondaryText,
            surfaceTint = JuicyOrange,
            surfaceContainerLowest = MangoCard,
            surfaceContainerLow = MangoCreamBackground,
            surfaceContainer = MangoSoftCream,
            surfaceContainerHigh = MangoSoftOrange,
            surfaceContainerHighest = MangoSoftOrange,
            outline = Color(0xFFAA9785),
            outlineVariant = Color(0xFFE7D8C7),
            error = Color(0xFFB3261E)
        )
    } else {
        darkColorScheme(
            primary = MangoYellow,
            onPrimary = MangoButtonInk,
            primaryContainer = MutedSurface2,
            onPrimaryContainer = Cream,
            secondary = JuicyOrange,
            onSecondary = MangoButtonInk,
            secondaryContainer = MutedSurface2,
            onSecondaryContainer = Cream,
            tertiary = LeafGreen,
            onTertiary = MangoButtonInk,
            background = DarkBackground,
            onBackground = Cream,
            surface = MutedSurface1,
            onSurface = Cream,
            surfaceVariant = MutedSurface2,
            onSurfaceVariant = GoldAccent,
            surfaceTint = LeafGreen,
            surfaceContainerLowest = DarkBackground,
            surfaceContainerLow = MutedSurface1,
            surfaceContainer = DarkBackground,
            surfaceContainerHigh = MutedSurface2,
            surfaceContainerHighest = MutedSurface2,
            outline = WarmBrown,
            error = Color(0xFFFF8C95)
        )
    }

    SideEffect {
        context.findActivity()?.let { activity ->
            val barStyle = if (light) {
                SystemBarStyle.light(
                    0xFFFFFAF3.toInt(),
                    0xFFFFFAF3.toInt()
                )
            } else {
                SystemBarStyle.dark(0xFF0B1017.toInt())
            }

            activity.enableEdgeToEdge(
                statusBarStyle = barStyle,
                navigationBarStyle = barStyle
            )

            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    com.example.ui.components.MangoMotionProvider {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp)
            ),
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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
