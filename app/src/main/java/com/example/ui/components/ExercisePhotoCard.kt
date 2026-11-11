package com.example.ui.components

import coil.compose.AsyncImage
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest

/** Local user-supplied photo, stored in app-private files rather than a temporary picker URI. */
internal object ExercisePhotoStore {
    private fun file(context: Context, exercise: String, stage: Int = 0): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(exercise.lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "exercise_photos")
        return File(directory, if (stage == 0) "$hash.jpg" else "${hash}_stage${stage}.jpg")
    }

    fun load(context: Context, exercise: String, stage: Int = 0) =
        runCatching { file(context, exercise, stage).takeIf { it.exists() }?.let({ path -> BitmapFactory.decodeFile(path) }) }
            .getOrNull()

    fun save(context: Context, exercise: String, uri: Uri, stage: Int = 0): Boolean = runCatching {
        val source = context.contentResolver.openInputStream(uri) ?: error("Nie można otworzyć zdjęcia")
        source.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: error("Niepoprawne zdjęcie")
            val width = original.width.coerceAtLeast(1)
            val height = original.height.coerceAtLeast(1)
            val scale = minOf(1f, 1200f / maxOf(width, height))
            val resized = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                original, (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1), true
            ) else original
            val target = file(context, exercise, stage)
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                check(resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output))
            }
            if (resized !== original) resized.recycle()
            original.recycle()
        }
        true
    }.getOrDefault(false)
}

@Composable
internal fun ExercisePhotoCard(exercise: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var stage by remember(exercise) { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            error = !ExercisePhotoStore.save(context, exercise, uri, stage)
            refresh++
        }
    }
    val bitmap = remember(exercise, stage, refresh) { ExercisePhotoStore.load(context, exercise, stage) }
    val curatedUrl = CuratedExercisePhotos.url(exercise, stage)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Galeria ćwiczenia", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Główne", "Start", "Ruch", "Powrót").forEachIndexed { index, title ->
                FilterChip(selected = stage == index, onClick = { stage = index },
                    label = { Text(title, style = MaterialTheme.typography.labelSmall) })
            }
        }
        Surface(shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().height(152.dp)
                .clickable { if (bitmap != null || curatedUrl != null) expanded = true else picker.launch("image/*") }) {
            Box(contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Zdjęcie ćwiczenia $exercise",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else if (curatedUrl != null) AsyncImage(model = curatedUrl,
                    contentDescription = "Fotografia poglądowa: $exercise",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SportExerciseIcons.forExercise(exercise), style = MaterialTheme.typography.headlineLarge)
                    Text("Dodaj zdjęcie ćwiczenia", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (bitmap == null && curatedUrl != null) Text(CuratedExercisePhotos.CREDIT,
            style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { picker.launch("image/*") }) {
            Text(if (bitmap == null) "📷 Wybierz zdjęcie" else "📷 Zmień zdjęcie")
        }
        if (ExercisePhotoLibrary.isApproved(context, exercise, stage)) {
            Text("✓ Zdjęcie przypisane do ćwiczenia i etapu",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            ExercisePhotoLibrary.attribution(context, exercise, stage)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
        }
        ExercisePhotoFinder(exercise, stage = stage, onSaved = { refresh++ })
        if (error) Text("Nie udało się zapisać zdjęcia.", color = MaterialTheme.colorScheme.error)
    }
    if (expanded && (bitmap != null || curatedUrl != null)) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(exercise) },
        text = { if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = exercise,
            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            else AsyncImage(model = curatedUrl, contentDescription = exercise,
                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("Zamknij") } })
}

internal data class ExerciseMovementStep(val title: String, val instruction: String)

internal object ExerciseMovementGuide {
    fun steps(exercise: String): List<ExerciseMovementStep> {
        val name = exercise.lowercase()
        return when {
            "hip thrust" in name || "glute bridge" in name -> listOf(
                ExerciseMovementStep("1. Pozycja", "Oprzyj łopatki stabilnie o ławkę, stopy ustaw na szerokość bioder."),
                ExerciseMovementStep("2. Unieś biodra", "Napnij pośladki i unieś biodra bez przeprostu lędźwi."),
                ExerciseMovementStep("3. Powrót", "Powoli opuść biodra, zachowując kontrolę.")
            )
            "przysiad" in name || "squat" in name -> listOf(
                ExerciseMovementStep("1. Ustawienie", "Ustaw stopy stabilnie i napnij tułów."),
                ExerciseMovementStep("2. Zejście", "Ugnij biodra i kolana, utrzymując stabilne pięty."),
                ExerciseMovementStep("3. Powrót", "Wstań płynnie, kontrolując ustawienie kolan.")
            )
            else -> listOf(
                ExerciseMovementStep("1. Przygotowanie", "Ustaw sprzęt i przyjmij stabilną pozycję."),
                ExerciseMovementStep("2. Ruch", "Wykonaj ruch powoli, w kontrolowanym zakresie."),
                ExerciseMovementStep("3. Powrót", "Wróć do pozycji początkowej bez szarpania.")
            )
        }
    }
}
