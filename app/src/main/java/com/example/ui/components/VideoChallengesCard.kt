package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.viewmodel.VideoChallenge

@Composable
fun VideoChallengesCard(
    challenges: List<VideoChallenge>, busy: Boolean,
    onCreate: (String, Int) -> Unit, onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit, onDuplicateWeek: (String, Int, Int) -> Unit,
    onAdd: (String, Int, String, Int, String, String) -> Unit,
    onToggle: (String, String) -> Unit, onRemove: (String, String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var weeks by rememberSaveable { mutableStateOf("4") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var selectedDay by rememberSaveable { mutableIntStateOf(1) }
    var videoTitle by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("20") }
    var category by rememberSaveable { mutableStateOf("Cardio") }
    var url by rememberSaveable { mutableStateOf("") }
    var fromWeek by rememberSaveable { mutableStateOf("1") }
    var toWeek by rememberSaveable { mutableStateOf("2") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selected = challenges.firstOrNull { it.id == selectedId } ?: challenges.firstOrNull()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🎬 Moje wyzwania i treningi wideo", style = MaterialTheme.typography.titleLarge)
        Text("Twórz dowolne plany, przypisuj treningi do dni i zapisuj postępy. Filmy pozostają na YouTube.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(title, { title = it.take(120) }, label = { Text("Nazwa nowego wyzwania") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(weeks, { weeks = it.filter(Char::isDigit).take(2) },
            label = { Text("Liczba tygodni (1–52)") }, singleLine = true)
        Button(onClick = { onCreate(title, weeks.toInt()); title = "" },
            enabled = !busy && title.isNotBlank() && weeks.toIntOrNull() in 1..52) {
            Text("+ Utwórz wyzwanie")
        }
        if (challenges.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(challenges, key = { it.id }) { challenge ->
                    FilterChip(selected = selected?.id == challenge.id,
                        onClick = { selectedId = challenge.id; selectedDay = 1 },
                        label = { Text(challenge.title) })
                }
            }
        }
        if (selected != null) {
            val done = selected.workouts.count { it.completedAt > 0 }
            Text("${selected.title} • ${done}/${selected.workouts.size} ukończonych",
                style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = {
                if (selected.workouts.isEmpty()) 0f else done.toFloat() / selected.workouts.size
            }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onDuplicate(selected.id) }, enabled = !busy) { Text("Duplikuj wyzwanie") }
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Usuń") }
            }
            if (confirmDelete) {
                Text("Usunąć wyzwanie i zapisane w nim postępy?")
                Row {
                    TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") }
                    TextButton(onClick = { onDelete(selected.id); selectedId = ""; confirmDelete = false },
                        enabled = !busy) { Text("Potwierdź usunięcie") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(fromWeek, { fromWeek = it.filter(Char::isDigit).take(2) },
                    label = { Text("Z tygodnia") }, modifier = Modifier.weight(1f))
                OutlinedTextField(toWeek, { toWeek = it.filter(Char::isDigit).take(2) },
                    label = { Text("Na tydzień") }, modifier = Modifier.weight(1f))
            }
            val from = fromWeek.toIntOrNull()
            val to = toWeek.toIntOrNull()
            TextButton(onClick = { onDuplicateWeek(selected.id, from!!, to!!) },
                enabled = !busy && from != null && to != null && from != to &&
                    from in 1..(selected.days / 7) && to in 1..(selected.days / 7)) {
                Text("Kopiuj tydzień (zastępuje docelowy)")
            }
            Text("Wybierz dzień:", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..selected.days).toList()) { day ->
                    val completed = selected.workouts.any { it.day == day && it.completedAt > 0 }
                    FilterChip(selected = selectedDay == day, onClick = { selectedDay = day },
                        label = { Text("${if (completed) "✓ " else ""}$day") })
                }
            }
            val day = selectedDay.coerceIn(1, selected.days)
            val dayWorkouts = selected.workouts.filter { it.day == day }
            Text("Dzień $day • ${dayWorkouts.sumOf { it.minutes }} min")
            dayWorkouts.forEach { workout ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${if (workout.completedAt > 0) "✅" else "▶"} ${workout.title} • ${workout.minutes} min",
                        style = MaterialTheme.typography.titleSmall)
                    Text(workout.category, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (workout.url.isNotBlank()) TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(workout.url))) }
                        }) { Text("YouTube") }
                        TextButton(onClick = { onToggle(selected.id, workout.id) }, enabled = !busy) {
                            Text(if (workout.completedAt > 0) "Cofnij ✓" else "Ukończ ✓")
                        }
                        TextButton(onClick = { onRemove(selected.id, workout.id) }, enabled = !busy) {
                            Text("Usuń")
                        }
                    }
                }
                HorizontalDivider()
            }
            Text("Dodaj trening do dnia $day", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(videoTitle, { videoTitle = it.take(120) },
                label = { Text("Nazwa treningu") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minuty") }, modifier = Modifier.weight(1f))
                OutlinedTextField(category, { category = it.take(60) },
                    label = { Text("Rodzaj") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(url, { url = it.take(500) }, label = { Text("Link YouTube (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            val validUrl = url.isBlank() || listOf("https://youtu.be/", "https://youtube.com/watch?",
                "https://www.youtube.com/watch?", "https://youtube.com/shorts/",
                "https://www.youtube.com/shorts/").any { url.startsWith(it) }
            Button(onClick = {
                onAdd(selected.id, day, videoTitle, minutes.toInt(), category, url)
                videoTitle = ""; url = ""
            }, enabled = !busy && videoTitle.isNotBlank() && category.isNotBlank() &&
                minutes.toIntOrNull() in 1..300 && validUrl,
                modifier = Modifier.fillMaxWidth()) { Text("+ Dodaj trening wideo") }
        }
    }
}
