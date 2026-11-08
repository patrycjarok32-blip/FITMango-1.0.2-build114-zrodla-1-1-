package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Lightweight perspective motion over the existing mascot image (not a true 3D model). */
@Composable
fun MangoMascotMotion(active: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMangoMotion.current
    val transition = rememberInfiniteTransition(label = "mangoPerspective")
    val yaw by transition.animateFloat(
        initialValue = -9f, targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mangoYaw"
    )
    val lift by transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mangoLift"
    )
    Box(modifier.graphicsLayer {
        rotationY = if (active && !reduced) yaw else 0f
        translationY = if (active && !reduced) lift.dp.toPx() else 0f
        cameraDistance = 14f * density
    }) { content() }
}

@Composable
fun MangoAiWorking(label: String, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMangoMotion.current
    val transition = rememberInfiniteTransition(label = "aiWorking")
    val glow by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "aiGlow"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(label, modifier = Modifier.alpha(if (reduced) 1f else glow), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Finite, event-triggered success celebration. No infinite animation or stored state. */
@Composable
fun MangoSuccessEffect(event: Int, message: String = "Zapisano!", modifier: Modifier = Modifier) {
    val reduced = LocalReducedMangoMotion.current
    var visible by remember { mutableStateOf(false) }
    val phase = remember { Animatable(1f) }
    LaunchedEffect(event, reduced) {
        if (event <= 0) return@LaunchedEffect
        visible = true
        phase.snapTo(if (reduced) 1f else 0f)
        if (!reduced) phase.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        delay(if (reduced) 1000 else 1100)
        visible = false
    }
    if (visible) {
        Box(modifier.testTag("mango_success_effect")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF388E3C))
                Text(message, style = MaterialTheme.typography.titleSmall)
            }
            if (!reduced && phase.value < 1f) {
                Canvas(Modifier.matchParentSize()) {
                    val colors = listOf(Color(0xFF4CAF50), Color(0xFFFFB74D), Color(0xFFFFD54F))
                    repeat(12) { i ->
                        val angle = (i * 2.0 * Math.PI / 12).toFloat()
                        val radius = size.minDimension * (0.15f + phase.value * .65f)
                        drawCircle(
                            color = colors[i % colors.size].copy(alpha = (1f - phase.value).coerceIn(0f, 1f)),
                            radius = 2.5.dp.toPx(),
                            center = Offset(size.width / 2f + kotlin.math.cos(angle) * radius,
                                size.height / 2f + kotlin.math.sin(angle) * radius)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MangoRevealCard(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMangoMotion.current
    var visible by remember { mutableStateOf(reduced) }
    LaunchedEffect(reduced) {
        if (reduced) visible = true else {
            delay((index.coerceIn(0, 8) * 55).toLong())
            visible = true
        }
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(tween(if (reduced) 0 else 350)) + slideInVertically(tween(if (reduced) 0 else 350)) { it / 5 }) {
        content()
    }
}
