package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class MangoMood { WELCOME, HAPPY, CELEBRATE, REST }

/** Drawn locally: no downloaded assets, new permissions, or stored user data. */
@Composable
fun MangoMoodIllustration(mood: MangoMood, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMangoMotion.current
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(mood, reduced) {
        bounce.snapTo(if (reduced) 1f else 0f)
        if (!reduced) bounce.animateTo(1f, tween(420))
    }
    Canvas(modifier.size(62.dp).testTag("mango_mood_illustration")) {
        val w = size.width
        val h = size.height
        val y = if (reduced) 0f else sin(bounce.value * PI).toFloat() * -h * .055f
        val center = Offset(w * .50f, h * .56f + y)
        drawOval(Color(0xFFFFB347), topLeft = Offset(w * .17f, h * .20f + y),
            size = androidx.compose.ui.geometry.Size(w * .66f, h * .70f))
        drawOval(Color(0xFFFF7950).copy(alpha = .30f),
            topLeft = Offset(w * .25f, h * .57f + y),
            size = androidx.compose.ui.geometry.Size(w * .50f, h * .22f))
        val leaf = Path().apply {
            moveTo(w * .49f, h * .23f + y)
            quadraticTo(w * .70f, h * -.03f + y, w * .81f, h * .16f + y)
            quadraticTo(w * .65f, h * .29f + y, w * .49f, h * .23f + y)
        }
        drawPath(leaf, Color(0xFF43976A))
        drawLine(Color(0xFF286C47), Offset(w * .50f, h * .23f + y),
            Offset(w * .54f, h * .12f + y), strokeWidth = w * .035f)
        val eyesY = h * .48f + y
        if (mood == MangoMood.REST) {
            for (x in listOf(.39f, .61f)) drawArc(Color(0xFF4C3525), 5f, 170f, false,
                topLeft = Offset(w * (x - .055f), eyesY - h * .02f),
                size = androidx.compose.ui.geometry.Size(w * .11f, h * .07f),
                style = Stroke(width = w * .024f))
        } else {
            for (x in listOf(.39f, .61f)) drawCircle(Color(0xFF4C3525), w * .027f,
                Offset(w * x, eyesY))
        }
        drawArc(Color(0xFF4C3525),
            if (mood == MangoMood.WELCOME) 15f else 5f,
            if (mood == MangoMood.WELCOME) 145f else 170f, false,
            topLeft = Offset(w * .40f, h * .52f + y),
            size = androidx.compose.ui.geometry.Size(w * .20f, h * .15f),
            style = Stroke(width = w * .025f))
        if (mood == MangoMood.CELEBRATE) {
            drawCircle(Color.White.copy(alpha = .90f), w * .025f, Offset(w * .86f, h * .29f))
            drawCircle(MangoCoral, w * .035f, Offset(w * .13f, h * .36f))
        }
    }
}

/** Only celebrates a real transition from below the goal, not on screen entry. */
@Composable
fun MangoGoalConfetti(goalReached: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMangoMotion.current
    var previous by remember { mutableStateOf(goalReached) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(goalReached, reduced) {
        val newlyReached = goalReached && !previous
        previous = goalReached
        if (newlyReached && !reduced) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(950))
        } else if (reduced) burst.snapTo(1f)
    }
    if (!reduced && burst.value < 1f) {
        Canvas(modifier.testTag("mango_goal_confetti")) {
            val colors = listOf(MangoCoral, MangoYellow, LeafGreen, WaterAccent)
            repeat(22) { i ->
                val angle = 2f * PI.toFloat() * i / 22f
                val radius = size.minDimension * (.08f + .43f * burst.value)
                drawCircle(colors[i % colors.size].copy(alpha = (1f - burst.value).coerceIn(0f, 1f)),
                    radius = (2 + i % 3).dp.toPx(),
                    center = Offset(size.width / 2 + cos(angle) * radius,
                        size.height / 2 + sin(angle) * radius))
            }
        }
    }
}
