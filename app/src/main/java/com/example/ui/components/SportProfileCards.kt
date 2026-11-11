package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Uses the existing sports_v1 storage; no Room migration or extra Health Connect permissions. */
@Composable
fun SportProfileCards(data: SportsData, today: LocalDate, busy: Boolean,
    onPlan: (String, String, Boolean) -> Unit, onRemovePlan: (String) -> Unit,
    onAddSet: (String, String, Int, Double) -> Unit,
    onSaveWeek: (List<SportPlan>) -> Unit = {},
    onMovePlan: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onCopyWeeks: (String, Int) -> Unit = { _, _ -> },
    onShiftUpcoming: (String, Int) -> Unit = { _, _ -> },
    onRemoveSet: (String) -> Unit = {},
    onUpdateExercises: (String, List<PlannedExercise>) -> Unit = { _, _ -> }) {
    var planDay by rememberSaveable { mutableStateOf(today.toString()) }
    var planTitle by rememberSaveable { mutableStateOf("Push") }
    var rest by rememberSaveable { mutableStateOf(false) }
    var exercise by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("10") }
    var weight by rememberSaveable { mutableStateOf("0") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showSetEditor by rememberSaveable { mutableStateOf(false) }
    var planMode by rememberSaveable { mutableStateOf("auto") }
    var showGenerator by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteDay by rememberSaveable { mutableStateOf<String?>(null) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var calendarMode by rememberSaveable { mutableStateOf("week") }
    var expandedCalendar by rememberSaveable { mutableStateOf(false) }
    var copyCount by rememberSaveable { mutableIntStateOf(1) }
    var showCopyConfirm by rememberSaveable { mutableStateOf(false) }
    var showShiftConfirm by rememberSaveable { mutableStateOf(false) }
    var movingDay by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationDay by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSwap by rememberSaveable { mutableStateOf(false) }
    var draggingFrom by remember { mutableStateOf<String?>(null) }
    var hoveredDay by remember { mutableStateOf<String?>(null) }
    val dayBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var strengthGoal by rememberSaveable { mutableStateOf("muscle") }
    var trainingDays by rememberSaveable { mutableStateOf("3") }
    var experience by rememberSaveable { mutableStateOf("beginner") }
    var trainingPlace by rememberSaveable { mutableStateOf("gym") }
    var generatedTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    val weekStart = SportsSupport.week(today).plusWeeks(weekOffset.toLong())
    val plans = remember(data.plans, weekStart) {
        (0L..6L).map { weekStart.plusDays(it) to data.plans.firstOrNull { p -> p.date == weekStart.plusDays(it).toString() } }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_profile")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SportHeading(Icons.Outlined.FitnessCenter, "Mój profil sportowy")
            Text("🥭 Twój plan • małe kroki, duży progres", style = MaterialTheme.typography.titleSmall, color = LeafGreen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportProfileSummary("Treningi", "${data.plans.count { !it.rest }}", Modifier.weight(1f))
                SportProfileSummary("Serie", "${data.sets.size}", Modifier.weight(1f))
                SportProfileSummary("Odpoczynek", "${data.plans.count { it.rest }}", Modifier.weight(1f))
            }
            TextButton(onClick = { showGenerator = !showGenerator }) { Text(if (showGenerator) "Ukryj generator −" else "✨ Utwórz plan +") }
            if (showGenerator) {
            Text("✨ Inteligentny Plan Siłowy 5.0", style = MaterialTheme.typography.titleMedium, color = LeafGreen)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = planMode == "auto", onClick = { planMode = "auto" },
                    label = { Text("✨ Generator") })
                FilterChip(selected = planMode == "manual", onClick = { planMode = "manual" },
                    label = { Text("✏️ Własny plan") })
            }
            if (planMode == "auto") {
                Text("Wybierz cel, a następnie edytuj propozycję przed zapisaniem.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Cel treningowy", style = MaterialTheme.typography.labelLarge)
                listOf("muscle" to "Budowa mięśni", "fat" to "Redukcja", "glutes" to "Nogi i pośladki",
                    "strength" to "Siła").chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { (key, label) ->
                            FilterChip(selected = strengthGoal == key, onClick = { strengthGoal = key },
                                label = { Text(label) })
                        }
                    }
                }
                Text("Dni treningowe", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (2..5).forEach { count ->
                        FilterChip(selected = trainingDays == count.toString(),
                            onClick = { trainingDays = count.toString() }, label = { Text("$count") })
                    }
                }
                Text("Doświadczenie", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("beginner" to "Start", "intermediate" to "Średni", "advanced" to "Zaaw.").forEach { (key, label) ->
                        FilterChip(selected = experience == key, onClick = { experience = key },
                            label = { Text(label) })
                    }
                }
                Text("Miejsce treningu", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("gym" to "Siłownia", "home" to "Dom", "mixed" to "Oba").forEach { (key, label) ->
                        FilterChip(selected = trainingPlace == key, onClick = { trainingPlace = key },
                            label = { Text(label) })
                    }
                }
                Button(onClick = {
                    generatedTitles = StrengthPlanSuggestions.generate(strengthGoal,
                        trainingDays.toInt(), experience, trainingPlace)
                }, modifier = Modifier.fillMaxWidth().testTag("sport_generate_plan")) {
                    Text("✨ Przygotuj propozycję tygodnia")
                }
                if (generatedTitles.isNotEmpty()) {
                    Text("Podgląd — możesz zmienić nazwy dni przed zapisaniem.",
                        style = MaterialTheme.typography.bodySmall)
                    generatedTitles.forEachIndexed { index, title ->
                        val date = weekStart.plusDays(index.toLong())
                        OutlinedTextField(value = title,
                            onValueChange = { value ->
                                generatedTitles = generatedTitles.toMutableList().also { it[index] = value.take(120) }
                            },
                            label = { Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale("pl")))) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Button(onClick = {
                        onSaveWeek(generatedTitles.mapIndexed { index, title ->
                            SportPlan(weekStart.plusDays(index.toLong()).toString(),
                                if (title.isBlank()) "Odpoczynek" else title, title.isBlank() || title == "Odpoczynek",
                                if (title.isBlank() || title == "Odpoczynek") emptyList() else StrengthSessionGenerator.exercises(title, experience, trainingPlace))
                        })
                    }, enabled = !busy && generatedTitles.any { it.isNotBlank() },
                        modifier = Modifier.fillMaxWidth().testTag("sport_save_generated_plan")) {
                        Text("Zapisz cały tydzień")
                    }
                    Text("Zapis zastępuje wyłącznie plan na te dni. Historia serii i Health Connect pozostają bez zmian.",
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Ręcznie zaplanuj wybrany dzień w edytorze poniżej.",
                    style = MaterialTheme.typography.bodySmall)
                LaunchedEffect(planMode) { showEditor = true }
            }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { weekOffset = (weekOffset - 1).coerceAtLeast(-52) }) { Text("← Tydzień") }
                Text(weekStart.format(DateTimeFormatter.ofPattern("dd.MM")) + "–" +
                    weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM")),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { weekOffset = (weekOffset + 1).coerceAtMost(52) }) { Text("Tydzień →") }
            }
            if (weekOffset != 0) TextButton(onClick = { weekOffset = 0 }) { Text("Wróć do bieżącego tygodnia") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCopyConfirm = true }, enabled = !busy,
                    modifier = Modifier.weight(1f)) { Text("⧉ Kopiuj tydzień") }
                OutlinedButton(onClick = { showShiftConfirm = true }, enabled = !busy,
                    modifier = Modifier.weight(1f)) { Text("↪ Przesuń cykl") }
            }
            if (showCopyConfirm) AlertDialog(
                onDismissRequest = { showCopyConfirm = false },
                title = { Text("Powtórz plan tygodnia") },
                text = {
                    Column {
                        Text("Skopiuj treningi i dni odpoczynku do kolejnych tygodni. Zajęte dni nie zostaną nadpisane.")
                        Row {
                            (1..4).forEach { n ->
                                FilterChip(selected = copyCount == n, onClick = { copyCount = n },
                                    label = { Text(n.toString()) }, modifier = Modifier.padding(end = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onCopyWeeks(weekStart.toString(), copyCount)
                        showCopyConfirm = false
                    }, enabled = !busy) { Text("Kopiuj") }
                },
                dismissButton = { TextButton(onClick = { showCopyConfirm = false }) { Text("Anuluj") } }
            )
            if (showShiftConfirm) AlertDialog(
                onDismissRequest = { showShiftConfirm = false },
                title = { Text("Przesuń kolejne treningi") },
                text = { Text("Przesunąć wszystkie zaplanowane dni od dzisiaj o 1 dzień? Historia treningów i wykonane serie nie zostaną zmienione.") },
                confirmButton = {
                    Button(onClick = {
                        onShiftUpcoming(today.toString(), 1)
                        showShiftConfirm = false
                    }, enabled = !busy) { Text("Przesuń o dzień") }
                },
                dismissButton = { TextButton(onClick = { showShiftConfirm = false }) { Text("Anuluj") } }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = calendarMode == "week", onClick = { calendarMode = "week" },
                    label = { Text("Tydzień") })
                FilterChip(selected = calendarMode == "month", onClick = { calendarMode = "month" },
                    label = { Text("Miesiąc") })
                TextButton(onClick = { expandedCalendar = !expandedCalendar }) {
                    Text(if (expandedCalendar) "Zwiń" else "Lista dni")
                }
            }
            if (calendarMode == "month") {
                val first = weekStart.withDayOfMonth(1)
                val gridStart = SportsSupport.week(first)
                val count = ((java.time.temporal.ChronoUnit.DAYS.between(gridStart,
                    first.plusMonths(1).minusDays(1)) + 7) / 7).toInt()
                (0 until count).forEach { rowIndex ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        (0..6).forEach { column ->
                            val day = gridStart.plusDays((rowIndex * 7 + column).toLong())
                            val scheduled = data.plans.firstOrNull { it.date == day.toString() }
                            Surface(modifier = Modifier.weight(1f).clickable {
                                weekOffset = java.time.temporal.ChronoUnit.WEEKS.between(
                                    SportsSupport.week(today), SportsSupport.week(day)).toInt()
                                expandedCalendar = true
                            }, shape = RoundedCornerShape(8.dp),
                                color = if (scheduled != null) LeafGreen.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                                        color = if (day.month == first.month) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outline)
                                    Text(when {
                                        scheduled?.rest == true -> "🌿"
                                        scheduled != null -> "●"
                                        else -> " "
                                    }, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Text("Dotknij dnia, aby przejść do jego tygodnia.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    plans.forEach { (date, plan) ->
                        Surface(modifier = Modifier.weight(1f).clickable {
                            expandedCalendar = true
                            planDay = date.toString()
                        }, shape = RoundedCornerShape(10.dp),
                            color = if (plan != null) LeafGreen.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.format(DateTimeFormatter.ofPattern("EE", Locale("pl"))),
                                    style = MaterialTheme.typography.labelSmall)
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                                Text(if (plan?.rest == true) "🌿" else if (plan != null) "●" else " ",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Text("Dotknij dnia, aby rozwinąć listę i przenieść trening.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("Plan tygodnia • zapis lokalny", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Przytrzymaj kafelek treningu i przeciągnij na inny dzień. Zmiana wymaga potwierdzenia.",
                style = MaterialTheme.typography.bodySmall)
            if (expandedCalendar) plans.forEach { (date, plan) ->
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("sport_plan_day_${date}")
                        .onGloballyPositioned { dayBounds[date.toString()] = it.boundsInRoot() }
                        .pointerInput(date, plan?.date, busy) {
                            if (plan != null && !busy) {
                                var anchor = Offset.Zero
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start ->
                                        draggingFrom = date.toString()
                                        anchor = (dayBounds[date.toString()]?.topLeft ?: Offset.Zero) + start
                                        dragPoint = anchor
                                        hoveredDay = date.toString()
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        anchor += delta
                                        dragPoint = anchor
                                        hoveredDay = dayBounds.entries.firstOrNull { (_, rect) ->
                                            rect.contains(anchor)
                                        }?.key
                                    },
                                    onDragEnd = {
                                        val destination = hoveredDay
                                        if (destination != null && destination != date.toString()) {
                                            movingDay = date.toString()
                                            destinationDay = destination
                                            confirmSwap = false
                                        }
                                        draggingFrom = null
                                        hoveredDay = null
                                        dragPoint = null
                                    },
                                    onDragCancel = {
                                        draggingFrom = null
                                        hoveredDay = null
                                        dragPoint = null
                                    }
                                )
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = if (draggingFrom != null && hoveredDay == date.toString()) LeafGreen.copy(alpha = 0.24f)
                        else if (date == today) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (date == today) LeafGreen.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale("pl"))),
                            modifier = Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.weight(1f)) {
                            Text(plan?.title ?: "Bez planu",
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            if (plan?.rest == true) Text("🌿 Odpoczynek", style = MaterialTheme.typography.labelSmall)
                            if (plan != null && !plan.rest) {
                                val completedSets = data.sets.count { it.date == date.toString() }
                                val plannedSets = plan.exercises.sumOf { it.sets }
                                val status = when {
                                    plannedSets > 0 && completedSets >= plannedSets -> "✓ Wykonany"
                                    completedSets > 0 -> "◐ W trakcie: ${completedSets}/${plannedSets} serii"
                                    date.isBefore(today) -> "○ Pominięty / bez zapisu"
                                    else -> "◷ Zaplanowany"
                                }
                                Text(status, style = MaterialTheme.typography.labelSmall,
                                    color = if (completedSets > 0) LeafGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (plan != null) TextButton(onClick = {
                            movingDay = date.toString()
                            destinationDay = date.plusDays(1).toString()
                        }, enabled = !busy) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Przenieś trening z ${date}")
                        }
                        if (plan != null) TextButton(onClick = { pendingDeleteDay = date.toString() },
                            enabled = !busy) {
                            Icon(Icons.Outlined.Close, contentDescription = "Usuń plan z ${date}")
                        }
                    }
                }
            }
            if (draggingFrom != null) Text("Przeciągasz: " + (data.plans.firstOrNull { it.date == draggingFrom }?.title ?: "") +
                " → " + (hoveredDay ?: "upuść na wybranym dniu"), color = LeafGreen)
            if (movingDay != null && destinationDay != null) AlertDialog(
                onDismissRequest = { movingDay = null; confirmSwap = false },
                title = { Text("Przenieś trening") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Z " + movingDay + " na inny dzień — także w kolejnym tygodniu.")
                        val destination = runCatching { LocalDate.parse(destinationDay) }.getOrNull()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                destination?.let { destinationDay = it.minusDays(1).toString() }
                            }) { Text("← Dzień") }
                            Text(destinationDay.orEmpty(), modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                destination?.let { destinationDay = it.plusDays(1).toString() }
                            }) { Text("Dzień →") }
                        }
                        OutlinedTextField(value = destinationDay.orEmpty(),
                            onValueChange = { destinationDay = it.take(10) },
                            label = { Text("Data docelowa RRRR-MM-DD") },
                            modifier = Modifier.fillMaxWidth())
                        val occupied = data.plans.firstOrNull { it.date == destinationDay }
                        if (occupied != null) {
                            Text("W tym dniu jest już: " + occupied.title)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = confirmSwap, onCheckedChange = { confirmSwap = it })
                                Text("Zamień miejscami oba dni")
                            }
                        } else Text("Dzień wolny — trening zostanie przeniesiony.")
                        Text("Historia i wykonane serie pozostaną bez zmian.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    val valid = runCatching { LocalDate.parse(destinationDay) }.getOrNull() != null &&
                        movingDay != destinationDay &&
                        (data.plans.none { it.date == destinationDay } || confirmSwap)
                    Button(enabled = valid && !busy, onClick = {
                        val from = movingDay
                        val to = destinationDay
                        if (from != null && to != null) onMovePlan(from, to, confirmSwap)
                        movingDay = null
                        confirmSwap = false
                    }) { Text(if (confirmSwap) "Zamień" else "Przenieś") }
                },
                dismissButton = {
                    TextButton(onClick = { movingDay = null; confirmSwap = false }) { Text("Anuluj") }
                }
            )
            if (pendingDeleteDay != null) AlertDialog(
                onDismissRequest = { pendingDeleteDay = null },
                title = { Text("Usunąć plan z tego dnia?") },
                text = { Text("Wykonane serie i historia pozostaną bez zmian.") },
                confirmButton = { TextButton(enabled = !busy, onClick = {
                    pendingDeleteDay?.let(onRemovePlan)
                    pendingDeleteDay = null
                }) { Text("Usuń plan") } },
                dismissButton = { TextButton(onClick = { pendingDeleteDay = null }) { Text("Anuluj") } })
            TextButton(onClick = { showEditor = !showEditor }) { Text(if (showEditor) "Zamknij edycję" else "Zaplanuj trening +") }
            if (showEditor) {
                OutlinedTextField(planDay, { planDay = it }, label = { Text("Data RRRR-MM-DD") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(planTitle, { planTitle = it.take(120) }, label = { Text("Nazwa treningu") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !rest)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rest, { rest = it }); Text("Dzień odpoczynku")
                }
                val valid = runCatching { LocalDate.parse(planDay) }.getOrNull() != null &&
                    (rest || planTitle.isNotBlank())
                Button(onClick = { onPlan(planDay, planTitle, rest) },
                    enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text("Zapisz plan") }
            }
            HorizontalDivider()
            var editorDate by rememberSaveable { mutableStateOf(today.toString()) }
            Text("🏋️ Trening i serie na wybrany dzień", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(7) { day ->
                    val date = weekStart.plusDays(day.toLong())
                    FilterChip(selected = editorDate == date.toString(),
                        onClick = { editorDate = date.toString() },
                        label = { Text(date.format(DateTimeFormatter.ofPattern("EE", Locale("pl")))) })
                }
            }
            val selectedPlan = data.plans.firstOrNull { it.date == editorDate }
            if (selectedPlan != null && !selectedPlan.rest) {
                Text("📋 Sesja: ${selectedPlan.title}", style = MaterialTheme.typography.titleMedium)
                if (selectedPlan.exercises.isEmpty()) {
                    Text("Ta sesja nie ma jeszcze dobranych ćwiczeń.")
                    TextButton(onClick = {
                        onUpdateExercises(editorDate, StrengthSessionGenerator.exercises(selectedPlan.title, "beginner", "gym"))
                    }, enabled = !busy) { Text("✨ Dobierz ćwiczenia do tej sesji") }
                }
                selectedPlan.exercises.forEachIndexed { index, planned ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${index + 1}. ${planned.name} • ${planned.sets} × ${planned.reps} • przerwa ${planned.restSeconds}s")
                        val prior = SportsSupport.previous(data, planned.name, editorDate)
                        if (prior.isNotEmpty()) {
                            val best = prior.maxByOrNull { it.kg * it.reps }
                            if (best != null) {
                                Text("Poprzednio: $"+"{best.kg} kg × $"+"{best.reps} powtórzeń",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Progresja: jeśli wszystkie serie wykonujesz poprawnie i z zapasem 1–2 powtórzeń, rozważ +1 powtórzenie lub mały wzrost ciężaru.",
                                    style = MaterialTheme.typography.bodySmall)
                                if (planned.reps < 100) TextButton(onClick = {
                                    onUpdateExercises(editorDate, selectedPlan.exercises.toMutableList().also {
                                        it[index] = planned.copy(reps = planned.reps + 1)
                                    })
                                }, enabled = !busy) { Text("✓ Zastosuj +1 powtórzenie w planie") }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                onUpdateExercises(editorDate, selectedPlan.exercises.toMutableList().also {
                                    it[index] = planned.copy(sets = (planned.sets - 1).coerceAtLeast(1))
                                })
                            }, enabled = !busy && planned.sets > 1) { Text("− seria") }
                            TextButton(onClick = {
                                onUpdateExercises(editorDate, selectedPlan.exercises.toMutableList().also {
                                    it[index] = planned.copy(sets = (planned.sets + 1).coerceAtMost(12))
                                })
                            }, enabled = !busy && planned.sets < 12) { Text("+ seria") }
                            TextButton(onClick = {
                                onUpdateExercises(editorDate, selectedPlan.exercises.filterIndexed { i, _ -> i != index })
                            }, enabled = !busy) { Text("Usuń") }
                        }
                    }
                }
                var addExercise by rememberSaveable { mutableStateOf("Hip thrust") }
                OutlinedTextField(addExercise, { addExercise = it.take(120) },
                    label = { Text("Dodaj ćwiczenie do sesji") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    onUpdateExercises(editorDate, selectedPlan.exercises + PlannedExercise(addExercise.trim(), 3, 10))
                }, enabled = !busy && addExercise.isNotBlank() && selectedPlan.exercises.size < 30) {
                    Text("+ Dodaj do planu")
                }
            }
            StrengthDayEditor(LocalDate.parse(editorDate), data, busy, onAddSet, onRemoveSet)
            HorizontalDivider()
            TextButton(onClick = { showSetEditor = !showSetEditor }) {
                Text(if (showSetEditor) "Zwiń zapis serii −" else "Zapisz serię siłową +")
            }
            if (showSetEditor) {
            OutlinedTextField(exercise, { exercise = it.take(120) }, label = { Text("Ćwiczenie") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reps, { reps = it }, label = { Text("Powtórzenia") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it }, label = { Text("Ciężar kg") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            val count = reps.toIntOrNull()
            val kg = weight.replace(',', '.').toDoubleOrNull()
            Button(onClick = { if (count != null && kg != null) onAddSet(today.toString(), exercise.trim(), count, kg) },
                enabled = !busy && exercise.isNotBlank() && count != null && count in 1..1000 &&
                    kg != null && kg.isFinite() && kg in 0.0..2000.0,
                modifier = Modifier.fillMaxWidth().testTag("sport_profile_add_set")) { Text("Zapisz serię") }
            }
        }
    }
    val recent = remember(data.sets, today) {
        data.sets.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d ->
            !d.isAfter(today) && !d.isBefore(today.minusDays(83)) } == true }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_strength_progress")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SportHeading(Icons.Outlined.TrendingUp, "Siła i rekordy osobiste")
            if (recent.none { it.kg > 0.0 }) Text("Zapisz serię z ciężarem, aby zobaczyć postępy z 12 tygodni.")
            else {
                val exercises = recent.filter { it.kg > 0.0 }.groupBy { it.exercise.trim().lowercase(Locale.ROOT) }
                exercises.entries.sortedByDescending { it.value.size }.take(5).forEach { (_, sets) ->
                    val best = sets.maxByOrNull { it.kg }!!
                    val volume = sets.sumOf { it.kg * it.reps }
                    Text("${best.exercise} • rekord ${formatSport(best.kg)} kg • objętość ${formatSport(volume)} kg",
                        style = MaterialTheme.typography.bodySmall)
                    val points = sets.sortedWith(compareBy<SportSet> { it.date }.thenBy { it.time }).takeLast(16)
                    if (points.size >= 2) {
                        val min = points.minOf { it.kg }
                        val max = points.maxOf { it.kg }
                        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                            val range = (max - min).coerceAtLeast(1.0)
                            val coords = points.mapIndexed { index, set ->
                                Offset(size.width * index / (points.size - 1),
                                    size.height * (1f - ((set.kg - min) / range).toFloat()))
                            }
                            coords.zipWithNext().forEach { (a, b) ->
                                drawLine(LeafGreen, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            coords.forEach { drawCircle(LeafGreen, radius = 3.dp.toPx(), center = it) }
                        }
                    }
                }
                Text("Rekord oznacza największy zapisany ciężar, a objętość sumę ciężar × powtórzenia. Nie są to szacunki maksymalnej siły.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    MangoCard(Modifier.fillMaxWidth().testTag("sport_recovery_guidance")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SportHeading(Icons.Outlined.AutoAwesome, "Wskazówki treningowe")
            val check = data.recovery.firstOrNull { it.date == today.toString() }
            val sleep = data.daily.firstOrNull { it.date == today.toString() }?.sleepMinutes
            val tip = when {
                check == null && sleep == null -> "Dodaj ocenę energii i zmęczenia lub zsynchronizuj sen, aby otrzymać wskazówkę."
                check != null && (check.fatigue >= 4 || check.energy <= 2) ->
                    "Zgłaszasz duże zmęczenie lub niską energię. Rozważ lżejszą sesję albo odpoczynek."
                sleep != null && sleep < 360 -> "Odczyt snu jest krótki. Rozważ spokojniejszy trening i zadbaj o regenerację."
                else -> "Jeśli czujesz się dobrze, realizuj plan i obserwuj technikę oraz samopoczucie."
            }
            Text(tip)
            Text("Wskazówki lokalne oparte na zapisanych danych, nie diagnoza medyczna ani zewnętrzny model AI.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sen: ${sleep?.let { "${it / 60} h ${it % 60} min" } ?: "brak odczytu"} • energia: ${check?.energy ?: "–"}/5 • zmęczenie: ${check?.fatigue ?: "–"}/5",
                style = MaterialTheme.typography.bodySmall)
            Text("Kalorie i białko sprawdzisz w zakładce Dieta; aktywność nie podnosi automatycznie limitu kalorii.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatSport(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun SportProfileSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
        }
    }
}

/** Local, deterministic suggestions; no external AI or additional permissions. */
internal object StrengthPlanSuggestions {
    fun generate(goal: String, days: Int, level: String, place: String): List<String> {
        require(days in 2..5)
        val split = when {
            goal == "glutes" -> listOf("Nogi i pośladki", "Góra ciała", "Pośladki i tył uda",
                "Góra ciała + core", "Nogi i pośladki")
            goal == "strength" -> listOf("Siła • dół", "Siła • góra", "Full Body • siła",
                "Siła • dół", "Siła • góra")
            days <= 3 -> listOf("Full Body A", "Full Body B", "Full Body C")
            else -> listOf("Góra ciała A", "Dół ciała A", "Góra ciała B", "Dół ciała B", "Full Body")
        }
        val result = MutableList(7) { "Odpoczynek" }
        val slots = when (days) {
            2 -> listOf(0, 3)
            3 -> listOf(0, 2, 4)
            4 -> listOf(0, 1, 3, 4)
            else -> listOf(0, 1, 2, 4, 5)
        }
        slots.forEachIndexed { index, day ->
            result[day] = split[index % split.size]
        }
        return result
    }
}

internal object StrengthSessionGenerator {
    /** Uses the same exercise catalogue shown in the library; never suggests unavailable equipment. */
    fun exercises(title: String, level: String, place: String): List<PlannedExercise> {
        val name = title.lowercase(Locale.ROOT)
        if ("odpoczynek" in name) return emptyList()
        val home = place == "home"
        val pool = StrengthExerciseLibrary.exercises.filter {
            !home || it.equipment == "Dom" || it.equipment == "Oba"
        }
        val groups = when {
            "poślad" in name -> listOf("Pośladki", "Nogi", "Pośladki", "Core", "Nogi")
            "nogi" in name || "dół" in name -> listOf("Nogi", "Pośladki", "Nogi", "Łydki", "Core")
            "pull" in name || "plecy" in name -> listOf("Plecy", "Plecy", "Biceps", "Plecy", "Core")
            "push" in name || "klatka" in name -> listOf("Klatka", "Barki", "Triceps", "Klatka", "Barki")
            "góra" in name -> listOf("Plecy", "Klatka", "Barki", "Biceps", "Triceps")
            else -> listOf("Nogi", "Plecy", "Klatka", "Pośladki", "Core")
        }
        val preferred = when {
            "poślad" in name -> listOf("Hip thrust", "Martwy ciąg rumuński", "Kickback na wyciągu")
            "pull" in name -> listOf("Wiosłowanie hantlami", "Ściąganie drążka")
            "push" in name -> listOf("Wyciskanie na ławce", "Wyciskanie hantli")
            else -> emptyList()
        }
        val beginner = level == "beginner"
        val advanced = level == "advanced"
        val chosen = mutableListOf<StrengthExercise>()
        groups.forEachIndexed { index, group ->
            val candidates = pool.filter { it.group == group && it !in chosen }
            val preferredCandidate = candidates.firstOrNull { it.name in preferred }
            val selected = if (beginner && index == 0) {
                candidates.firstOrNull { "maszynie" in it.name.lowercase(Locale.ROOT) || "Suwnica" in it.name }
                    ?: preferredCandidate
            } else preferredCandidate
            val candidate = selected ?: candidates.getOrNull(
                (if (advanced) index + 2 else index) % candidates.size.coerceAtLeast(1))
            if (candidate != null) chosen += candidate
        }
        return chosen.map { exercise ->
            PlannedExercise(exercise.name, if (beginner) 2 else if (advanced) 4 else 3,
                if (advanced) 8 else 10, if (advanced) 120 else 90)
        }
    }
}
