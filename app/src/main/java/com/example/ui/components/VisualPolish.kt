package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.RecipeCategories
import kotlinx.coroutines.delay

@Composable
fun AppearanceSettingsCard() {
    val prefs = LocalContext.current.getSharedPreferences(MangoAppearance.PREFS, 0)
    var guide by remember { mutableStateOf(prefs.getBoolean("mangoGuide", true)) }
    CollapsibleMangoCard("Wygląd aplikacji", "appearance") {
        Text("Motyw kolorystyczny")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!MangoAppearance.light, { prefs.edit().putBoolean(MangoAppearance.KEY, false).apply() },
                label = { Text("Ciemny") }, modifier = Modifier.testTag("theme_dark"))
            FilterChip(MangoAppearance.light, { prefs.edit().putBoolean(MangoAppearance.KEY, true).apply() },
                label = { Text("Jasny kremowy") }, modifier = Modifier.testTag("theme_light"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Mały przewodnik mango", Modifier.weight(1f)); Switch(guide, { guide = it; prefs.edit().putBoolean("mangoGuide", it).apply() })
        }
        Text("Wybór zapisujemy na telefonie i w kopii danych.", style = MaterialTheme.typography.bodySmall)
    }
}

fun mealCategoryColor(category: String): Color = when (RecipeCategories.canonical(category)) {
    "Śniadanie" -> MangoYellow
    "Obiad" -> ActivityAccent
    "Kolacja" -> DinnerAccent
    else -> WaterAccent
}
fun mealCategoryIcon(category: String): ImageVector = when (RecipeCategories.canonical(category)) {
    "Śniadanie" -> Icons.Outlined.WbSunny
    "Obiad" -> Icons.Outlined.Restaurant
    "Kolacja" -> Icons.Outlined.NightsStay
    else -> Icons.Outlined.LocalCafe
}

@Composable
fun Modifier.mangoResultAppearance(key: Any?): Modifier {
    val reduced = LocalReducedMangoMotion.current
    val phase = remember { Animatable(1f) }
    LaunchedEffect(key, reduced) {
        if (!reduced) { phase.snapTo(.65f); phase.animateTo(1f, tween(170)) }
        else phase.snapTo(1f)
    }
    return graphicsLayer { alpha = phase.value; translationY = (1f - phase.value) * 12.dp.toPx() }
}

@Composable
fun SuccessFeedback(event: Int, text: String = "Dodano posiłek", tag: String = "saved_action_feedback") {
    var visible by remember { mutableStateOf(false) }
    val reduced = LocalReducedMangoMotion.current
    val view = androidx.compose.ui.platform.LocalView.current
    val burst = remember { Animatable(1f) }
    LaunchedEffect(event, reduced) {
        if (event > 0) {
            runCatching { view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
            visible = true
            if (!reduced) {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(750))
            } else burst.snapTo(1f)
            delay(750)
            visible = false
        }
    }
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(if (reduced) 0 else 160)),
        exit = fadeOut(tween(if (reduced) 0 else 120))
    ) {
        Box(Modifier.fillMaxWidth()) {
            Surface(shape = MaterialTheme.shapes.medium, color = LeafGreen.copy(alpha = .15f)) {
                Row(Modifier.fillMaxWidth().padding(10.dp)) {
                    MangoGuide(event = event, size = 26.dp)
                    Icon(Icons.Outlined.CheckCircle, null, tint = LeafGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("$text ✓", color = LeafGreen, modifier = Modifier.testTag(tag))
                }
            }
            if (!reduced && burst.value < 1f) {
                androidx.compose.foundation.Canvas(Modifier.matchParentSize().testTag("success_confetti")) {
                    val colors = listOf(LeafGreen, MangoYellow, JuicyOrange)
                    repeat(12) { i ->
                        val angle = i * (2.0 * Math.PI / 12.0)
                        val distance = size.minDimension * (0.1f + burst.value * 0.55f)
                        val x = size.width / 2f + kotlin.math.cos(angle).toFloat() * distance
                        val y = size.height / 2f + kotlin.math.sin(angle).toFloat() * distance
                        drawCircle(
                            color = colors[i % colors.size].copy(alpha = 1f - burst.value),
                            radius = 2.5.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}
