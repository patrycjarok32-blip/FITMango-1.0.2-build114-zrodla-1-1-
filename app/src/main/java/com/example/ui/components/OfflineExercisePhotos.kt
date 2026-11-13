package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
