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
        StrengthExercise("Dead bug", "Core", "Dom", "Utrzymuj stabilny tułów."),
        StrengthExercise("Przysiad goblet", "Nogi", "Oba", "Trzymaj ciężar blisko klatki piersiowej."),
        StrengthExercise("Przysiad bułgarski", "Nogi", "Oba", "Utrzymuj stabilne biodra."),
        StrengthExercise("Przysiad sumo", "Nogi", "Oba", "Kolana prowadź w linii stóp."),
        StrengthExercise("Hack squat", "Nogi", "Siłownia", "Kontroluj głębokość przysiadu."),
        StrengthExercise("Przysiad Smitha", "Nogi", "Siłownia", "Ustaw stopy stabilnie."),
        StrengthExercise("Step-up", "Nogi", "Oba", "Odbijaj się głównie nogą na podwyższeniu."),
        StrengthExercise("Wykroki chodzone", "Nogi", "Oba", "Kontroluj każdy krok."),
        StrengthExercise("Zakroki", "Nogi", "Oba", "Utrzymuj wyprostowany tułów."),
        StrengthExercise("Wspięcia na palce stojąc", "Łydki", "Oba", "Zatrzymaj ruch w górnej pozycji."),
        StrengthExercise("Wspięcia na palce siedząc", "Łydki", "Siłownia", "Wykonuj pełny kontrolowany zakres."),
        StrengthExercise("Hip thrust na maszynie", "Pośladki", "Siłownia", "Napnij pośladki bez przeprostu pleców."),
        StrengthExercise("Kickback na wyciągu", "Pośladki", "Siłownia", "Nie kołysz miednicą."),
        StrengthExercise("Odwodzenie nogi z gumą", "Pośladki", "Dom", "Utrzymuj napięcie gumy."),
        StrengthExercise("Frog pumps", "Pośladki", "Dom", "Kontroluj ruch miednicy."),
        StrengthExercise("Martwy ciąg klasyczny", "Plecy", "Siłownia", "Utrzymuj ciężar blisko ciała."),
        StrengthExercise("Martwy ciąg sumo", "Nogi", "Siłownia", "Ustaw kolana w linii stóp."),
        StrengthExercise("Wiosłowanie sztangą", "Plecy", "Siłownia", "Zachowaj stabilną pozycję tułowia."),
        StrengthExercise("Wiosłowanie na maszynie", "Plecy", "Siłownia", "Nie szarp ciężaru."),
        StrengthExercise("Wiosłowanie na wyciągu siedząc", "Plecy", "Siłownia", "Ściągnij łopatki w końcowej fazie."),
        StrengthExercise("Face pull", "Barki", "Siłownia", "Prowadź łokcie na boki."),
        StrengthExercise("Pullover na wyciągu", "Plecy", "Siłownia", "Utrzymuj lekko ugięte łokcie."),
        StrengthExercise("Podciąganie z asystą", "Plecy", "Siłownia", "Kontroluj opuszczanie."),
        StrengthExercise("Wyciskanie hantli na ławce", "Klatka", "Oba", "Utrzymuj stabilne łopatki."),
        StrengthExercise("Wyciskanie na skosie dodatnim", "Klatka", "Siłownia", "Nie odrywaj bioder od ławki."),
        StrengthExercise("Rozpiętki z hantlami", "Klatka", "Oba", "Nie opuszczaj ramion zbyt głęboko."),
        StrengthExercise("Rozpiętki na maszynie", "Klatka", "Siłownia", "Kontroluj fazę powrotną."),
        StrengthExercise("Rozpiętki na bramie", "Klatka", "Siłownia", "Nie szarp linek."),
        StrengthExercise("Pompki na kolanach", "Klatka", "Dom", "Zachowaj linię tułowia."),
        StrengthExercise("Wyciskanie żołnierskie", "Barki", "Siłownia", "Napnij brzuch i pośladki."),
        StrengthExercise("Arnold press", "Barki", "Oba", "Wykonuj płynny obrót hantli."),
        StrengthExercise("Unoszenie hantli w przód", "Barki", "Oba", "Nie kołysz tułowiem."),
        StrengthExercise("Odwrotne rozpiętki", "Barki", "Oba", "Prowadź ruch z tylnej części barków."),
        StrengthExercise("Reverse pec deck", "Barki", "Siłownia", "Nie unoś barków do uszu."),
        StrengthExercise("Uginanie młotkowe", "Biceps", "Oba", "Utrzymuj łokcie blisko ciała."),
        StrengthExercise("Uginanie na modlitewniku", "Biceps", "Siłownia", "Nie prostuj gwałtownie łokci."),
        StrengthExercise("Uginanie na wyciągu", "Biceps", "Siłownia", "Unikaj kołysania tułowiem."),
        StrengthExercise("Uginanie koncentracyjne", "Biceps", "Oba", "Kontroluj opuszczanie hantla."),
        StrengthExercise("Francuskie wyciskanie", "Triceps", "Oba", "Stabilizuj łokcie."),
        StrengthExercise("Prostowanie tricepsa nad głową", "Triceps", "Oba", "Nie wyginaj dolnych pleców."),
        StrengthExercise("Dipy na poręczach", "Triceps", "Siłownia", "Nie schodź poniżej komfortowego zakresu."),
        StrengthExercise("Prostowanie tricepsa z gumą", "Triceps", "Dom", "Utrzymuj nieruchome łokcie."),
        StrengthExercise("Russian twist", "Core", "Dom", "Obracaj tułów powoli."),
        StrengthExercise("Bird dog", "Core", "Dom", "Utrzymuj miednicę nieruchomo."),
        StrengthExercise("Side plank", "Core", "Dom", "Utrzymuj biodra w jednej linii."),
        StrengthExercise("Spięcia brzucha na wyciągu", "Core", "Siłownia", "Zginaj tułów, nie ciągnij rękami."),
        StrengthExercise("Unoszenie kolan w zwisie", "Core", "Siłownia", "Nie bujaj ciałem."),
        StrengthExercise("Pallof press", "Core", "Oba", "Opieraj się rotacji tułowia."),
        StrengthExercise("Farmer walk", "Full Body", "Oba", "Idź stabilnie z napiętym brzuchem."),
        StrengthExercise("Kettlebell swing", "Full Body", "Oba", "Ruch napędzaj biodrami, nie ramionami."),
        StrengthExercise("Thruster z hantlami", "Full Body", "Oba", "Łącz przysiad z płynnym wyciskaniem."),
        StrengthExercise("Wiosłowanie gumą", "Plecy", "Dom", "Ściągaj łopatki bez unoszenia barków.")
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
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("Wszystkie", "Nogi", "Pośladki", "Plecy", "Klatka", "Barki", "Biceps", "Triceps", "Core", "Łydki", "Full Body")) { category ->
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
