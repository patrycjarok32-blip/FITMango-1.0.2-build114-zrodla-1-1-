package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

private val mango = Color(0xFFFFB547)
private val coral = Color(0xFFFF805D)
private val leaf = Color(0xFF83C890)

enum class MangoDayPhase { MORNING, DAY, EVENING, NIGHT }

@Composable
fun rememberMangoDayPhase(): MangoDayPhase {
    val context = LocalContext.current
    val preference = remember(context) { context.getSharedPreferences("FITMangoPremium", 0) }
    var automatic by remember { mutableStateOf(preference.getBoolean("automaticDayPhase", false)) }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    DisposableEffect(preference) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "automaticDayPhase") automatic = preference.getBoolean("automaticDayPhase", false)
        }
        preference.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preference.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        hour = LocalTime.now().hour
        automatic = preference.getBoolean("automaticDayPhase", false)
    }
    return if (!automatic) MangoDayPhase.DAY else when (hour) {
        in 5..10 -> MangoDayPhase.MORNING
        in 11..16 -> MangoDayPhase.DAY
        in 17..20 -> MangoDayPhase.EVENING
        else -> MangoDayPhase.NIGHT
    }
}

@Composable
fun MangoDayPhaseSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("FITMangoPremium", 0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("automaticDayPhase", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Kolory zależne od pory dnia", modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            prefs.edit().putBoolean("automaticDayPhase", it).apply()
        })
    }
    Text("Domyślnie wyłączone; nie zmienia wybranego jasnego lub ciemnego motywu.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun MangoLivingBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val reduced = LocalReducedMangoMotion.current
    val phase = rememberMangoDayPhase()
    val transition = rememberInfiniteTransition(label = "mango_background")
    val motion by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "mango_glow")
    val light = when (phase) {
        MangoDayPhase.MORNING -> mango
        MangoDayPhase.DAY -> leaf
        MangoDayPhase.EVENING -> coral
        MangoDayPhase.NIGHT -> Color(0xFF547B6A)
    }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(light.copy(alpha = .075f), Color.Transparent)))
            drawCircle(light.copy(alpha = .055f), radius = size.width * .62f,
                center = Offset(size.width * (if (reduced) .6f else .4f + motion * .3f), size.height * .16f))
        }
        content()
    }
}

@Composable
fun PremiumCalorieRing(consumed: Int?, target: Int, incomplete: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMangoMotion.current
    val safeTarget = target.coerceAtLeast(1)
    val fraction = ((consumed ?: 0).toFloat() / safeTarget).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(if (reduced) 0 else 520), label = "premium_calorie_ring")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                drawArc(Color.Gray.copy(alpha = .16f), -90f, 360f, false, style = Stroke(stroke))
                drawArc(brush = Brush.sweepGradient(listOf(coral, mango, leaf, coral)), startAngle = -90f,
                    sweepAngle = 360f * animated, useCenter = false,
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (consumed == null) "—" else "${safeTarget - consumed}", style = MaterialTheme.typography.titleLarge)
                Text("kcal do celu", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(if (incomplete) "Bilans niepełny" else "${consumed ?: 0} / $safeTarget kcal",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PremiumWaterGlass(waterMl: Int, goalMl: Int = 2000, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMangoMotion.current
    val fraction = (waterMl.coerceAtLeast(0).toFloat() / goalMl.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(if (reduced) 0 else 650), label = "premium_water")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(width = 60.dp, height = 85.dp)) {
            val left = size.width * .16f
            val right = size.width * .84f
            val top = size.height * .08f
            val bottom = size.height * .93f
            drawRoundRect(Color(0xFFB8DCE7).copy(alpha = .25f),
                topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right-left,bottom-top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            val height = (bottom-top) * animated
            drawRect(Color(0xFF49BCE8).copy(alpha = .82f),
                topLeft = Offset(left+3.dp.toPx(), bottom-height),
                size = androidx.compose.ui.geometry.Size(right-left-6.dp.toPx(),height))
            drawLine(Color(0xFF78B2C8),Offset(left,top),Offset(left+4.dp.toPx(),bottom),2.dp.toPx())
            drawLine(Color(0xFF78B2C8),Offset(right,top),Offset(right-4.dp.toPx(),bottom),2.dp.toPx())
        }
        Column {
            Text("Nawodnienie", style = MaterialTheme.typography.titleSmall)
            Text("$waterMl ml", style = MaterialTheme.typography.bodyMedium)
            Text("${(fraction*100).toInt()}% celu $goalMl ml", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Five original, resolution-independent growth illustrations drawn directly in Compose. */
@Composable
fun MangoGrowthTree(activeDays: Int, modifier: Modifier = Modifier) {
    val stage = when { activeDays >= 21 -> 4; activeDays >= 14 -> 3; activeDays >= 7 -> 2; activeDays >= 3 -> 1; else -> 0 }
    val reduced = LocalReducedMangoMotion.current
    val growth by animateFloatAsState((stage + 1) / 5f,
        tween(if (reduced) 0 else 650), label = "mango_tree_growth")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(92.dp, 104.dp)) {
            val cx = size.width / 2f
            val soilY = size.height * .88f
            val trunkTop = soilY - size.height * (.13f + growth * .42f)
            drawOval(Color(0xFFD4A16D), topLeft = Offset(size.width * .13f, soilY),
                size = androidx.compose.ui.geometry.Size(size.width * .74f, size.height * .1f))
            drawLine(Color(0xFF805E42), Offset(cx, soilY), Offset(cx, trunkTop),
                strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (stage >= 1) {
                drawOval(Color(0xFF83C890), topLeft = Offset(cx - size.width * .31f, trunkTop),
                    size = androidx.compose.ui.geometry.Size(size.width * .31f, size.height * .2f))
            }
            if (stage >= 2) {
                drawOval(Color(0xFF57A86B), topLeft = Offset(cx, trunkTop - size.height * .03f),
                    size = androidx.compose.ui.geometry.Size(size.width * .34f, size.height * .23f))
            }
            if (stage >= 3) {
                drawCircle(Color(0xFF65B276), radius = size.width * .25f,
                    center = Offset(cx, trunkTop + size.height * .02f))
                drawCircle(Color(0xFF83C890), radius = size.width * .19f,
                    center = Offset(cx - size.width * .18f, trunkTop + size.height * .09f))
            }
            if (stage >= 4) {
                drawOval(mango, topLeft = Offset(cx + size.width * .07f, trunkTop + size.height * .08f),
                    size = androidx.compose.ui.geometry.Size(size.width * .16f, size.height * .21f))
                drawOval(coral, topLeft = Offset(cx - size.width * .27f, trunkTop + size.height * .11f),
                    size = androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .18f))
            }
        }
        Column {
            Text("Moje drzewko Mango", style = MaterialTheme.typography.titleSmall)
            Text("${activeDays.coerceAtLeast(0)} dni aktywności • etap ${stage+1}/5", style = MaterialTheme.typography.bodySmall)
            Text("Rośnie dzięki regularnym zapisom, nie zmianom wagi.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
