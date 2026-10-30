package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.data.MealPlanEntity
import com.example.ui.theme.*

fun dropDayAt(position: Offset, days: Map<String, Rect>): String? = days.entries.firstOrNull { it.value.contains(position) }?.key

class MealDragController(private val onDrop: (MealPlanEntity, String) -> Unit) {
    var meal by mutableStateOf<MealPlanEntity?>(null); private set
    var position by mutableStateOf(Offset.Zero); private set
    private var week = ""
    private val days = mutableStateMapOf<String, Rect>()
    fun register(date: String, bounds: Rect, weekKey: String) {
        if (week != weekKey) { days.clear(); week = weekKey }
        days[date] = bounds
    }
    fun begin(value: MealPlanEntity, at: Offset) { meal = value; position = at }
    fun move(amount: Offset) { position += amount }
    fun target(): String? = if (meal == null) null else dropDayAt(position, days)
    fun cancel() { meal = null }
    fun finish() {
        val current = meal; val date = target(); meal = null
        if (current != null && date != null && current.dayOfWeek != date) onDrop(current, date)
    }
}

@Composable
fun Modifier.mealDragHandle(controller: MealDragController, meal: MealPlanEntity): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.boundsInRoot().topLeft }
        .pointerInput(controller, meal) {
            detectDragGesturesAfterLongPress(onDragStart = { controller.begin(meal, origin + it) },
                onDragCancel = controller::cancel, onDragEnd = controller::finish) { change, delta ->
                change.consume(); controller.move(delta)
            }
        }
}

@Composable
fun MealDragOverlay(controller: MealDragController) {
    var root by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot().topLeft }) {
        controller.meal?.let { meal ->
            Surface(Modifier.offset { IntOffset((controller.position.x - root.x - 100).toInt(), (controller.position.y - root.y - 60).toInt()) }
                .width(200.dp).testTag("dragged_meal"), shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                color = MutedSurface2, shadowElevation = 6.dp) {
                Column(Modifier.padding(10.dp)) {
                    Text(meal.recipeTitle, maxLines = 1)
                    Text(controller.target()?.let { "Upuść: $it" } ?: "Przeciągnij na dzień", color = MangoYellow)
                }
            }
        }
    }
}
