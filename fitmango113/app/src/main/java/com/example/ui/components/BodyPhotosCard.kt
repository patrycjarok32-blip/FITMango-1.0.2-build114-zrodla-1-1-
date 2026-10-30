package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.BodyPhotoEntity
import com.example.ui.theme.*
import com.example.viewmodel.BodyPhotoAngle
import com.example.viewmodel.photo

@Composable
fun BodyPhotosCard(photos: List<BodyPhotoEntity>,
    onSave: (String, String, String, (String?) -> Unit) -> Unit,
    onReplace: (Long, BodyPhotoAngle, String, (String?) -> Unit) -> Unit,
    onDelete: (Long, (String?) -> Unit) -> Unit,
    onSecureLegacy: () -> Unit, newFirst: Boolean = false) {
    val context = LocalContext.current
    var front by rememberSaveable { mutableStateOf("") }
    var side by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var angleName by rememberSaveable { mutableStateOf(BodyPhotoAngle.FRONT.name) }
    var targetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var visible by rememberSaveable { mutableIntStateOf(4) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var delete by remember { mutableStateOf<BodyPhotoEntity?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var compareAngle by rememberSaveable { mutableStateOf(BodyPhotoAngle.FRONT.name) }
    val ordered = remember(photos) { photos.sortedByDescending { it.id } }
    val pair = selected.mapNotNull { id -> photos.find { it.id == id } }.sortedBy { it.id }
    LaunchedEffect(Unit) { onSecureLegacy() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val angle = BodyPhotoAngle.valueOf(angleName)
            val id = targetId
            if (id == null) when (angle) {
                BodyPhotoAngle.FRONT -> front = uri.toString()
                BodyPhotoAngle.SIDE -> side = uri.toString()
                BodyPhotoAngle.BACK -> back = uri.toString()
            } else {
                busy = true; message = null
                onReplace(id, angle, uri.toString()) { error -> busy = false; message = error ?: "Zdjęcie zapisane w zestawie." }
            }
        }
        targetId = null
    }
    fun choose(id: Long?, angle: BodyPhotoAngle) {
        targetId = id; angleName = angle.name
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val composer: @Composable () -> Unit = {
        Text("Nowy zestaw • dzisiaj", style = MaterialTheme.typography.titleSmall)
        Text("Możesz zapisać jeden, dwa lub trzy widoki. Zdjęcia zostaną skopiowane do pamięci aplikacji.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyPhotoAngle.entries.forEach { angle ->
                val uri = when (angle) { BodyPhotoAngle.FRONT -> front; BodyPhotoAngle.SIDE -> side; BodyPhotoAngle.BACK -> back }
                Column(Modifier.weight(1f)) {
                    BodyPhotoTile(uri, "Nowe zdjęcie • ${angle.label}", Modifier.fillMaxWidth().aspectRatio(.8f), enabled = !busy) { choose(null, angle) }
                    TextButton(enabled = !busy, onClick = { choose(null, angle) }, contentPadding = PaddingValues(0.dp)) { Text(angle.label) }
                    if (uri.isNotBlank()) TextButton(enabled = !busy, onClick = {
                        when (angle) { BodyPhotoAngle.FRONT -> front = ""; BodyPhotoAngle.SIDE -> side = ""; BodyPhotoAngle.BACK -> back = "" }
                    }, contentPadding = PaddingValues(0.dp)) { Text("Wyczyść", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        if (listOf(front, side, back).any { it.isNotBlank() }) {
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("body_photos_save"), onClick = {
                busy = true; message = null
                onSave(front, side, back) { error ->
                    busy = false
                    if (error == null) { front = ""; side = ""; back = ""; visible = maxOf(visible, 4) }
                    message = error ?: "Zestaw zapisany w historii zdjęć."
                }
            }) { Text(if (busy) "Zapisywanie…" else "Zapisz zestaw zdjęć") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Twoje zdjęcia sylwetki", style = MaterialTheme.typography.titleMedium)
        Text("Otwórz miniaturę, aby zobaczyć pełne zdjęcie. Wybierz dwa zestawy do porównania.", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, modifier = Modifier.testTag("body_photo_message")) }
        if (newFirst) composer()
        Text("Zapisane zestawy: ${photos.size}", style = MaterialTheme.typography.titleSmall)
        if (photos.isEmpty()) Text("Nie masz jeszcze zapisanych zestawów. Dodaj pierwsze zdjęcie w formularzu nowego zestawu.")
        ordered.take(visible).forEach { row ->
            Column(Modifier.fillMaxWidth().testTag("body_photo_set_${row.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.date, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BodyPhotoAngle.entries.forEach { angle ->
                        Column(Modifier.weight(1f)) {
                            BodyPhotoTile(row.photo(angle), "${angle.label} • ${row.date}", Modifier.fillMaxWidth().aspectRatio(.72f)) {
                                if (row.photo(angle).isBlank()) choose(row.id, angle)
                                else preview = row.photo(angle) to "${angle.label} • ${row.date}"
                            }
                            Text(angle.label, style = MaterialTheme.typography.labelMedium)
                            TextButton(enabled = !busy, onClick = { choose(row.id, angle) }, contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.testTag("body_photo_replace_${row.id}_${angle.name}")) {
                                Text(if (row.photo(angle).isBlank()) "Dodaj" else "Wybierz ponownie", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FilterChip(selected = row.id in selected, enabled = !busy,
                        onClick = { selected = if (row.id in selected) selected - row.id else (selected + row.id).takeLast(2) },
                        label = { Text(if (row.id in selected) "Wybrano ✓" else "Do porównania") })
                    TextButton(enabled = !busy, onClick = { delete = row }) { Text("Usuń zestaw") }
                }
                HorizontalDivider()
            }
        }
        if (visible < photos.size) TextButton(onClick = { visible += 4 }, modifier = Modifier.testTag("body_photos_more")) {
            Text("Pokaż starsze (${photos.size - visible})")
        }
        if (photos.size >= 2) Button(enabled = pair.size == 2 && !busy, onClick = { comparing = true }, modifier = Modifier.testTag("body_photos_compare")) {
            Text("Porównaj wybrane (${pair.size}/2)")
        }
        if (!newFirst) composer()
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Jeśli stare zdjęcie jest niedostępne, wskaż je ponownie przy właściwym widoku — data i pozostałe zdjęcia zestawu zostaną zachowane.", style = MaterialTheme.typography.bodySmall)
    }
    if (comparing && pair.size == 2) {
        BodyPhotoViewer("Porównanie sylwetki", onClose = { comparing = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyPhotoAngle.entries.forEach { angle -> FilterChip(compareAngle == angle.name, { compareAngle = angle.name }, label = { Text(angle.label) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { row -> Column(Modifier.weight(1f)) {
                    Text(row.date, style = MaterialTheme.typography.labelLarge)
                    val angle = BodyPhotoAngle.valueOf(compareAngle)
                    BodyPhotoTile(row.photo(angle), "${angle.label} • ${row.date}", Modifier.fillMaxWidth().height(380.dp), fit = true) {
                        if (row.photo(angle).isNotBlank()) preview = row.photo(angle) to "${angle.label} • ${row.date}"
                    }
                } }
            }
        }
    }
    preview?.let { (uri, title) ->
        BodyPhotoViewer(title, onClose = { preview = null }) {
            BodyPhotoTile(uri, title, Modifier.fillMaxWidth().height(480.dp), fit = true) {}
        }
    }
    delete?.let { row -> AlertDialog(onDismissRequest = { if (!busy) delete = null },
        title = { Text("Usunąć zestaw z ${row.date}?") },
        text = { Text("Zestaw zniknie z historii aplikacji. Oryginalne zdjęcia w galerii telefonu pozostaną.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            onDelete(row.id) { error -> busy = false; message = error ?: "Zestaw usunięty."; if (error == null) { selected = selected - row.id; comparing = false; delete = null } }
        }) { Text("Usuń") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { delete = null }) { Text("Anuluj") } }) }
}

@Composable
private fun BodyPhotoViewer(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(DarkBackground).systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onClose) { Text("Zamknij") }
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun BodyPhotoTile(uri: String, label: String, modifier: Modifier, enabled: Boolean = true, fit: Boolean = false, onClick: () -> Unit) {
    var failed by remember(uri) { mutableStateOf(false) }
    var loading by remember(uri) { mutableStateOf(uri.isNotBlank()) }
    Box(modifier.background(MutedSurface2).clickable(enabled = enabled && (uri.isBlank() || !failed)) { onClick() }, contentAlignment = Alignment.Center) {
        if (uri.isNotBlank()) AsyncImage(model = uri, contentDescription = label, modifier = Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            onSuccess = { failed = false; loading = false }, onError = { failed = true; loading = false })
        if (uri.isBlank() || failed) Text(if (failed) "Zdjęcie niedostępne" else "Brak zdjęcia", Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall)
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
    }
}
