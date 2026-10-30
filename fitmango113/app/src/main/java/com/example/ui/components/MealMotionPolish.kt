package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.example.data.MealPlanEntity
import com.example.viewmodel.ScaledMealNutrition
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/** Retain departing cards only for their short exit; database state is never delayed. */
@Composable
fun rememberMealQueueMotion(meals: List<MealPlanEntity>, day: String): List<MealPlanEntity> {
    val rendered = remember(day) { meals.toMutableStateList() }
    val reduced = LocalReducedMangoMotion.current
    LaunchedEffect(meals, day, reduced) {
        meals.forEachIndexed { index, meal ->
            val old = rendered.indexOfFirst { it.id == meal.id }
            if (old >= 0) rendered[old] = meal else rendered.add(index.coerceAtMost(rendered.size), meal)
        }
        if (rendered.any { old -> meals.none { it.id == old.id } }) {
            if (!reduced) Animatable(0f).animateTo(1f, tween(220))
            rendered.removeAll { old -> meals.none { it.id == old.id } }
        }
    }
    return rendered.toList()
}

@Composable
fun MealQueueMotionItem(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMangoMotion.current
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    AnimatedVisibility(transition,
        enter = fadeIn(tween(if (reduced) 0 else 150)) + expandVertically(tween(if (reduced) 0 else 180)),
        exit = fadeOut(tween(if (reduced) 0 else 100)) + shrinkVertically(tween(if (reduced) 0 else 180))) { content() }
}

@Composable
fun Modifier.mangoDayChange(epochDay: Long): Modifier {
    val reduced = LocalReducedMangoMotion.current
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableLongStateOf(epochDay) }
    var direction by remember { mutableIntStateOf(1) }
    LaunchedEffect(epochDay, reduced) {
        val changed = previous != epochDay
        direction = if (epochDay >= previous) 1 else -1
        previous = epochDay
        if (changed && !reduced) { progress.snapTo(0f); progress.animateTo(1f, tween(180)) }
        else progress.snapTo(1f)
    }
    return graphicsLayer {
        alpha = if (reduced) 1f else .75f + progress.value * .25f
        translationX = if (reduced) 0f else direction * (1f - progress.value) * 18.dp.toPx()
    }
}

@Composable
fun AnimatedMealNutrition(nutrition: ScaledMealNutrition, tag: String, prefix: String = "") {
    val reduced = LocalReducedMangoMotion.current
    val signature = listOf(nutrition.calories, nutrition.protein, nutrition.carbs, nutrition.fat, nutrition.isPartial)
    var previous by remember { mutableStateOf(signature) }
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(signature, reduced) {
        val changed = signature != previous; previous = signature
        if (changed && !reduced) { highlight.snapTo(1f); highlight.animateTo(0f, tween(650)) }
        else highlight.snapTo(0f)
    }
    val calories = mangoNumber(nutrition.calories ?: 0)
    val protein = mangoNumber(nutrition.protein ?: 0)
    val carbs = mangoNumber(nutrition.carbs ?: 0)
    val fat = mangoNumber(nutrition.fat ?: 0)
    val parts = listOfNotNull(nutrition.calories?.let { "$calories kcal" },
        nutrition.protein?.let { "B: ${protein}g" }, nutrition.carbs?.let { "W: ${carbs}g" }, nutrition.fat?.let { "T: ${fat}g" })
    Text(if (parts.isEmpty()) "Brak danych o wartościach odżywczych" else prefix + parts.joinToString(" • ") + if (nutrition.isPartial) " • niepełne dane" else "",
        modifier = Modifier.testTag(tag).background(LeafGreen.copy(alpha = if (reduced) 0f else .18f * highlight.value), MaterialTheme.shapes.small).padding(2.dp),
        color = MangoYellow, style = MaterialTheme.typography.labelMedium)
}

enum class NutritionStatus { COMPLETE, MISSING, IN_PROGRESS }

@Composable
fun NutritionStatusBadge(status: NutritionStatus, label: String, modifier: Modifier = Modifier) {
    val icon = when (status) { NutritionStatus.COMPLETE -> Icons.Outlined.CheckCircle; NutritionStatus.MISSING -> Icons.Outlined.Edit; NutritionStatus.IN_PROGRESS -> Icons.Outlined.AccessTime }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = if (status == NutritionStatus.COMPLETE) LeafGreen else GoldAccent, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (status == NutritionStatus.COMPLETE) LeafGreen else GoldAccent)
    }
}

/** Anchored, keyboard-accessible menu with one finite appearance animation. */
@Composable
fun MangoActionsMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    val reduced = LocalReducedMangoMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) { if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(120)) }
    val position = remember { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
            val y = if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) anchorBounds.bottom else anchorBounds.top - popupContentSize.height
            return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
        }
    } }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(modifier = Modifier.graphicsLayer {
            alpha = if (reduced) 1f else progress.value
            scaleX = if (reduced) 1f else .97f + .03f * progress.value; scaleY = scaleX
            transformOrigin = TransformOrigin(0f, 0f)
        }, shape = MaterialTheme.shapes.medium, shadowElevation = 6.dp) {
            Column(Modifier.widthIn(min = 200.dp, max = 280.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp), content = content)
        }
    }
}
