package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.MutedSurface1
import com.example.viewmodel.MealPlannerSupport

@Composable
fun CompactStartCard(title: String, summary: String, id: String, quickAction: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val duration = if (LocalReducedMangoMotion.current) 0 else 220
    MangoCard(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("compact_start_$id")) {
            MangoSectionArt(sectionArtwork(id), sectionArtworkAccent(id), size = 44.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.GoldAccent)
            }
            Text(if (expanded) "Zwiń −" else "Rozwiń +")
        }
        if (!expanded && quickAction != null) TextButton(onClick = quickAction, modifier = Modifier.testTag("compact_water_add")) { Text("Woda +250 ml") }
        AnimatedVisibility(expanded, enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** Keep actions below the scrollable body and above the keyboard/navigation bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangoEditSheet(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {}, modifier: Modifier = Modifier,
    containerColor: Color = MutedSurface1, properties: DialogProperties = DialogProperties(),
    dismissEnabled: Boolean = true) {
    val allowDismiss by rememberUpdatedState(dismissEnabled)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowDismiss })
    ModalBottomSheet(onDismissRequest = { if (allowDismiss) onDismissRequest() }, sheetState = state,
        containerColor = containerColor) {
        Column(modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title()
            Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { text() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) { dismissButton(); Spacer(Modifier.width(8.dp)); confirmButton() }
        }
    }
}

fun swipeDayDelta(distance: Float, threshold: Float): Long = when {
    distance <= -threshold -> 1L
    distance >= threshold -> -1L
    else -> 0L
}

@Composable
fun Modifier.swipePlannerDays(day: String, onDay: (String) -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val change by rememberUpdatedState(onDay)
    return pointerInput(day, threshold) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
            onDragEnd = {
                val delta = swipeDayDelta(distance, threshold)
                if (delta != 0L) MealPlannerSupport.dateForDay(day)?.let { change(it.plusDays(delta).toString()) }
                distance = 0f
            }) { pointer, amount -> pointer.consume(); distance += amount }
    }
}
