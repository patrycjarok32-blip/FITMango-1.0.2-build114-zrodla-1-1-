package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime

/** Purely visual additions: no new storage, no changes to diet or hydration totals. */
@Composable
fun MangoDailyGreeting(today: LocalDate, activeDates: List<String>, waterMl: Int, consumed: Int, calorieGoal: Int) {
    val reduced = LocalReducedMangoMotion.current
    val hour = remember { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..10 -> "Dzień dobry! ☀️"
        in 11..16 -> "Miłego dnia! 🌤️"
        in 17..21 -> "Dobry wieczór! 🌙"
        else -> "Spokojnej nocy! 🌙"
    }
    val dateSet = remember(activeDates) { activeDates.toSet() }
    val first = if (today.toString() in dateSet) today else today.minusDays(1)
    var streak = 0
    while (streak < 365 && first.minusDays(streak.toLong()).toString() in dateSet) streak++
    val goalReached = calorieGoal > 0 && consumed >= calorieGoal
    val goalProgress by animateFloatAsState(
        targetValue = if (goalReached) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 550), label = "daily_goal_glow"
    )
    var hydrationNotice by remember { mutableStateOf(false) }
    var previousWater by remember { mutableIntStateOf(waterMl) }
    LaunchedEffect(waterMl, reduced) {
        hydrationNotice = !reduced && waterMl > previousWater
        previousWater = waterMl
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("mango_daily_delight")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MangoGuide(message = greeting, event = if (reduced) 0 else consumed + waterMl, size = 40.dp,
                modifier = Modifier.weight(1f))
        }
        Text("🥭 Seria regularności: $streak dni", style = MaterialTheme.typography.bodySmall,
            color = GoldAccent, modifier = Modifier.testTag("mango_streak"))
        AnimatedVisibility(visible = goalReached, enter = fadeIn(tween(if (reduced) 0 else 400)),
            exit = fadeOut(tween(if (reduced) 0 else 100))) {
            Text(if (goalProgress > .9f) "✨ Dzisiejszy cel kalorii osiągnięty!" else "Cel osiągnięty!",
                color = LeafGreen, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("mango_goal_celebration"))
        }
        AnimatedVisibility(visible = hydrationNotice, enter = fadeIn(tween(300)),
            exit = fadeOut(tween(150))) {
            Text("💧 Woda zapisana! Świetnie!", color = WaterAccent,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("mango_water_feedback"))
        }
    }
}

@Composable
fun MangoAnimatedWaterGlass(waterMl: Int, goalMl: Int = 2000) {
    val reduced = LocalReducedMangoMotion.current
    val fill by animateFloatAsState(
        targetValue = (waterMl.toFloat() / goalMl.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(if (reduced) 0 else 750), label = "mango_water_fill"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("mango_animated_water")) {
        Box(Modifier.width(48.dp).height(68.dp).clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp,
            topStart = 5.dp, topEnd = 5.dp)).background(MutedSurface2)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter)
                .background(WaterAccent.copy(alpha = .7f)))
        }
        Column {
            Text("Nawodnienie • $waterMl ml", style = MaterialTheme.typography.bodyMedium, color = Cream)
            Text("Wizualizacja względem 2000 ml • nie zmienia Twojego celu",
                style = MaterialTheme.typography.labelSmall, color = GoldAccent)
        }
    }
}
