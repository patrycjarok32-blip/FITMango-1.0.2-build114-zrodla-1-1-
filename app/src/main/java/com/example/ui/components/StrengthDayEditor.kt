package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.*
import java.time.LocalDate
import kotlinx.coroutines.delay

internal data class StrengthExercise(val name: String, val group: String, val equipment: String, val tip: String)

internal object StrengthExerciseLibrary {
    val exercises = listOf(
        StrengthExercise("Przysiad", "Nogi", "Siłownia", "Utrzymuj stabilny tułów i kontroluj zejście."),
        StrengthExercise("Suwnica na nogi", "Nogi", "Siłownia", "Nie odrywaj bioder od oparcia."),
        StrengthExercise("Wykroki", "Nogi", "Oba", "Utrzymuj równowagę i kontroluj kolano."),
        StrengthExercise("Prostowanie nóg", "Nogi", "Siłownia", "Ruch wykonuj bez szarpania."),
        StrengthExercise("Uginanie nóg", "Nogi", "Siłownia", "Kontroluj powrót ciężaru."),
        StrengthExercise("Hip thrust", "Pośladki", "Oba", "Nie przeprostowuj odcinka lędźwiowego."),
        StrengthExercise("Martwy ciąg rumuński", "Pośladki", "Oba", "Prowadź ciężar blisko nóg."),
        StrengthExercise("Odwodzenie nóg", "Pośladki", "Siłownia", "Nie kołysz tułowiem."),
        StrengthExercise("Glute bridge", "Pośladki", "Dom", "Napnij pośladki w górnej fazie."),
        StrengthExercise("Wiosłowanie hantlami", "Plecy", "Oba", "Łopatki prowadź stabilnie."),
        StrengthExercise("Ściąganie drążka", "Plecy", "Siłownia", "Nie odchylaj nadmiernie tułowia."),
        StrengthExercise("Podciąganie", "Plecy", "Oba", "Użyj asysty, gdy potrzebujesz."),
        StrengthExercise("Wyciskanie na ławce", "Klatka", "Siłownia", "Stopy i łopatki utrzymuj stabilnie."),
        StrengthExercise("Pompki", "Klatka", "Dom", "Zachowaj prostą linię ciała."),
        StrengthExercise("Wyciskanie hantli", "Barki", "Oba", "Nie wyginaj pleców."),
        StrengthExercise("Unoszenie bokiem", "Barki", "Oba", "Użyj kontrolowanego zakresu ruchu."),
        StrengthExercise("Uginanie na biceps", "Ramiona", "Oba", "Nie kołysz tułowiem."),
        StrengthExercise("Prostowanie na triceps", "Ramiona", "Siłownia", "Łokcie trzymaj blisko ciała."),
        StrengthExercise("Plank", "Core", "Oba", "Oddychaj spokojnie i nie zapadaj bioder."),
        StrengthExercise("Dead bug", "Core", "Dom", "Utrzymuj stabilny tułów.")
    )
    fun suggestKg(previous: List<SportSet>, repsTarget: Int): Double? {
        if (previous.size < 2 || previous.any { it.reps < repsTarget }) return null
        val last = previous.last().kg
        if (last <= 0.0) return null
        return ((last + 2.5) * 2).toInt() / 2.0
    }
}

@Composable
internal fun StrengthDayEditor(
    date: LocalDate,
    data: SportsData,
    busy: Boolean,
    onAddSet: (String, String, Int, Double) -> Unit,
    onRemoveSet: (String) -> Unit
) {
    var selected by rememberSaveable(date.toString()) { mutableStateOf("Hip thrust") }
    var group by rememberSaveable { mutableStateOf("Wszystkie") }
    var search by rememberSaveable { mutableStateOf("") }
    var repetitions by rememberSaveable(date.toString(), selected) { mutableStateOf("10") }
    var weight by rememberSaveable(date.toString(), selected) { mutableStateOf("0") }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var restSeconds by rememberSaveable { mutableIntStateOf(90) }
    var remaining by rememberSaveable { mutableIntStateOf(0) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(timerRunning, remaining) {
        if (timerRunning && remaining > 0) {
            delay(1000)
            remaining -= 1
        } else if (remaining == 0) timerRunning = false
    }
    val previous = SportsSupport.previous(data, selected, date.toString())
    val todaySets = data.sets.filter { it.date == date.toString() }
    val target = repetitions.toIntOrNull()
    val suggestion = StrengthExerciseLibrary.suggestKg(previous, target ?: 10)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📚 Biblioteka ćwiczeń", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { showLibrary = !showLibrary }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showLibrary) "Zamknij bibliotekę" else "Wybierz ćwiczenie • $selected")
        }
        if (showLibrary) {
            OutlinedTextField(search, { search = it }, label = { Text("Szukaj ćwiczenia") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Wszystkie", "Nogi", "Pośladki", "Plecy").forEach { category ->
                    FilterChip(selected = group == category, onClick = { group = category },
                        label = { Text(category) })
                }
            }
            StrengthExerciseLibrary.exercises.filter {
                (group == "Wszystkie" || it.group == group) &&
                    (search.isBlank() || it.name.contains(search, ignoreCase = true))
            }.forEach { item ->
                TextButton(onClick = { selected = item.name; showLibrary = false },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("${item.name} • ${item.group} • ${item.equipment}")
                }
            }
        }
        StrengthExerciseLibrary.exercises.firstOrNull { it.name == selected }?.let {
            Text("Technika: ${it.tip}", style = MaterialTheme.typography.bodySmall)
        }
        Text("📝 Zapis serii • ${date}", style = MaterialTheme.typography.titleMedium)
        if (previous.isNotEmpty()) {
            Text("Ostatni trening: " + previous.joinToString(" • ") { "${it.kg} kg × ${it.reps}" },
                style = MaterialTheme.typography.bodySmall)
        }
        if (suggestion != null) {
            TextButton(onClick = { weight = suggestion.toString() }) {
                Text("✨ Sugestia progresji: ${suggestion} kg (zwiększ tylko przy dobrej technice)")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(repetitions, { repetitions = it }, label = { Text("Powtórzenia") },
                singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(weight, { weight = it }, label = { Text("Ciężar kg") },
                singleLine = true, modifier = Modifier.weight(1f))
        }
        val kg = weight.replace(',', '.').toDoubleOrNull()
        Button(onClick = { if (target != null && kg != null) onAddSet(date.toString(), selected, target, kg) },
            enabled = !busy && !date.isAfter(LocalDate.now()) && target != null && target in 1..1000 &&
                kg != null && kg.isFinite() && kg in 0.0..2000.0,
            modifier = Modifier.fillMaxWidth()) { Text("✓ Zapisz wykonaną serię") }
        todaySets.forEach { set ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${set.exercise}: ${set.kg} kg × ${set.reps}", modifier = Modifier.weight(1f))
                TextButton(onClick = { onRemoveSet(set.id) }, enabled = !busy) { Text("Usuń") }
            }
        }
        HorizontalDivider()
        Text("⏱️ Timer przerwy", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 90, 120).forEach { seconds ->
                FilterChip(selected = restSeconds == seconds, onClick = { restSeconds = seconds },
                    label = { Text("${seconds}s") })
            }
        }
        Text("Pozostało: ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { remaining = restSeconds; timerRunning = true }) { Text("Start") }
            OutlinedButton(onClick = { timerRunning = !timerRunning }, enabled = remaining > 0) {
                Text(if (timerRunning) "Pauza" else "Wznów")
            }
            TextButton(onClick = { remaining = 0; timerRunning = false }) { Text("Reset") }
        }
    }
}
