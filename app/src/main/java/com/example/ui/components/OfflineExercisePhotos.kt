package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest

/** Copies user-selected real exercise photos into private storage for offline use. */
object OfflineExercisePhotos {
    private fun path(context: Context, exercise: String, step: Int): File {
        require(step in 0..10)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(exercise.trim().lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val dir = File(context.filesDir, "workout_technique").apply { mkdirs() }
        return File(dir, "$digest-$step.jpg")
    }
    fun load(context: Context, exercise: String, step: Int): Bitmap? {
        val file = path(context, exercise, step)
        return if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    fun save(context: Context, exercise: String, step: Int, uri: Uri) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Nie można odczytać zdjęcia")
        val bytes = input.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var count: Int
            while (stream.read(chunk).also { count = it } != -1) {
                require(buffer.size() + count <= 15_000_000) { "Zdjęcie jest za duże" }
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        require(opts.outWidth in 1..12000 && opts.outHeight in 1..12000) { "Niepoprawny obraz" }
        val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 1200)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Nie można odczytać obrazu")
        val target = path(context, exercise, step)
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
            }
            check(temp.renameTo(target)) { "Nie udało się zapisać zdjęcia" }
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }
}

@Composable
fun OfflineExercisePhotoStep(exercise: String, step: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var revision by remember(exercise, step) { mutableIntStateOf(0) }
    var error by remember(exercise, step) { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { OfflineExercisePhotos.save(context, exercise, step, uri) }
                .onSuccess { revision++; error = null }
                .onFailure { error = it.message ?: "Nie udało się zapisać zdjęcia" }
        }
    }
    val photo = remember(exercise, step, revision) {
        OfflineExercisePhotos.load(context, exercise, step)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (photo != null) {
            Image(photo.asImageBitmap(), contentDescription = "Zdjęcie etapu ruchu",
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                contentScale = ContentScale.Fit)
        }
        OutlinedButton(onClick = { launcher.launch("image/*") }) {
            Text(if (photo == null) "Dodaj zdjęcie etapu (offline)" else "Zmień zdjęcie etapu")
        }
        if (photo == null) Text("Po dodaniu zdjęcie będzie dostępne bez internetu.",
            style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun WorkoutTechniquePhotos(exercise: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("FITMangoTechniqueVariants", android.content.Context.MODE_PRIVATE) }
    val variants = if (exercise.trim().equals("Wyprost biodra (na stronę)", ignoreCase = true))
        listOf("Wyprost biodra na wyciągu", "Wyprost biodra z gumą", "Wyprost biodra w klęku")
        else emptyList()
    var selected by remember(exercise) { mutableStateOf(prefs.getString(exercise, null)?.takeIf { it in variants }) }
    if (variants.isNotEmpty()) {
        Text("Wybierz wariant ćwiczenia, aby zobaczyć właściwą technikę.")
        variants.forEach { variant ->
            TextButton(onClick = { selected = variant; prefs.edit().putString(exercise, variant).apply() }) {
                Text(if (selected == variant) "✓ $variant" else variant)
            }
        }
        if (selected == null) return
    }
    WorkoutTechniquePhotoContent(exercise, selected ?: exercise)
}

@Composable
private fun WorkoutTechniquePhotoContent(exercise: String, mediaExercise: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDetails by remember(exercise) { mutableStateOf(false) }
    Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val frames = remember(exercise, mediaExercise) {
            (0..1).map { step ->
                OfflineExercisePhotos.load(context, exercise, step)
                    ?: ExercisePhotoStore.load(context, exercise, step + 1)
                    ?: CuratedExercisePhotos.illustrationUrl(mediaExercise, step + 1)
                        ?.substringAfter("/images/flat/", "")?.takeIf { it.isNotEmpty() }
                        ?.let { name -> runCatching { context.assets.open("repdb/$name").use { BitmapFactory.decodeStream(it) } }.getOrNull() }
            }
        }
        var animate by remember(exercise, mediaExercise) { mutableStateOf(false) }
        var frame by remember(exercise, mediaExercise) { mutableIntStateOf(0) }
        if (frames.all { it != null }) {
            TextButton(onClick = { animate = !animate }) {
                Text(if (animate) "Zatrzymaj pokaz" else "Pokaż ruch offline (bez dźwięku)")
            }
            LaunchedEffect(animate, exercise, mediaExercise) {
                frame = 0
                while (animate) {
                    kotlinx.coroutines.delay(900)
                    frame = 1 - frame
                }
            }
            if (animate) frames[frame]?.let { bitmap ->
                Image(bitmap.asImageBitmap(), contentDescription = if (frame == 0) "Pozycja początkowa" else "Pozycja końcowa",
                    modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
            }
        }
        if (!animate) for (step in 0..1) {
            val photo = remember(exercise, step) {
                OfflineExercisePhotos.load(context, exercise, step)
                    ?: ExercisePhotoStore.load(context, exercise, step + 1)
            }
            val url = CuratedExercisePhotos.url(mediaExercise, step + 1)
            val fallback = CuratedExercisePhotos.illustrationUrl(mediaExercise, step + 1)
            var useIllustration by remember(mediaExercise, step) { mutableStateOf(false) }
            val displayedUrl = if (useIllustration) fallback else url
            val localRep = fallback?.substringAfter("/images/flat/", "")?.takeIf { it.isNotEmpty() }?.let { name ->
                remember(name) { runCatching { context.assets.open("repdb/$name").use { BitmapFactory.decodeStream(it) } }.getOrNull() }
            }
            Text(if (step == 0) "Pozycja początkowa" else "Pozycja końcowa",
                style = MaterialTheme.typography.labelLarge)
            if (photo != null) {
                Image(photo.asImageBitmap(), contentDescription = "$exercise — etap ${step + 1}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit)
            } else if (localRep != null) {
                Image(localRep.asImageBitmap(), contentDescription = "$exercise — ilustracja RepDB", modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit)
            } else if (displayedUrl != null) {
                var failed by remember(displayedUrl) { mutableStateOf(false) }
                coil.compose.AsyncImage(model = coil.request.ImageRequest.Builder(context).data(displayedUrl).diskCachePolicy(coil.request.CachePolicy.ENABLED).memoryCachePolicy(coil.request.CachePolicy.ENABLED).build(), contentDescription = "$exercise — etap ${step + 1}",
                    modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit,
                    onError = { if (fallback != null && displayedUrl != fallback) useIllustration = true else failed = true }, onSuccess = { failed = false })
                if (failed) Text("Zdjęcie niedostępne. Sprawdź połączenie lub dodaj własne.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Brak przypisanego zdjęcia dla tego wariantu.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (CuratedExercisePhotos.hasPhotos(mediaExercise)) Text(CuratedExercisePhotos.CREDIT,
            style = MaterialTheme.typography.labelSmall)
        if (CuratedExercisePhotos.illustrationUrl(mediaExercise, 1) != null) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri("https://repdb.co") }) { Text(CuratedExercisePhotos.REPDB_CREDIT) }
        }
        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Ukryj wskazówki i zdjęcia własne" else "Wskazówki i zdjęcia własne")
        }
        if (showDetails) {
            ExerciseMovementGuide.steps(exercise).forEachIndexed { index, step ->
                Text(step.title, style = MaterialTheme.typography.labelLarge)
                Text(step.instruction, style = MaterialTheme.typography.bodySmall)
                OfflineExercisePhotoStep(exercise, index)
            }
        }
    }
}
