package com.example.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.BodyPhotoEntity
import com.example.data.DietaDao
import com.example.data.PersonalDataGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class BodyPhotoAngle(val label: String) { FRONT("Przód"), SIDE("Bok"), BACK("Tył") }
fun BodyPhotoEntity.photo(angle: BodyPhotoAngle) = when (angle) {
    BodyPhotoAngle.FRONT -> frontUri; BodyPhotoAngle.SIDE -> sideUri; BodyPhotoAngle.BACK -> backUri
}
private fun BodyPhotoEntity.withPhoto(angle: BodyPhotoAngle, uri: String) = when (angle) {
    BodyPhotoAngle.FRONT -> copy(frontUri = uri); BodyPhotoAngle.SIDE -> copy(sideUri = uri); BodyPhotoAngle.BACK -> copy(backUri = uri)
}

/** Own private copies outlive the Photo Picker's temporary permission grants. */
class BodyPhotoStore(private val context: Context, private val dao: DietaDao) {
    private val directory get() = File(context.filesDir, "body-photos").apply { mkdirs() }

    private suspend fun copyPhoto(source: String): String {
        require(source.isNotBlank()) { "Wybierz zdjęcie." }
        val uri = Uri.parse(source)
        require(uri.scheme in listOf("content", "file")) { "Wybierz zdjęcie z telefonu." }
        val temporary = File(directory, "${UUID.randomUUID()}.tmp")
        val target = File(directory, "${UUID.randomUUID()}.img")
        try {
            val input = if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream()
                else context.contentResolver.openInputStream(uri) ?: error("Nie można odczytać zdjęcia. Wybierz je ponownie.")
            input.use { stream -> temporary.outputStream().use { output ->
                val buffer = ByteArray(8192); var bytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer); if (count < 0) break
                    bytes += count; require(bytes <= 64L * 1024 * 1024) { "Zdjęcie przekracza 64 MB." }
                    output.write(buffer, 0, count)
                }
            } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Nie można odczytać obrazu. Wybierz inne zdjęcie." }
            check(temporary.renameTo(target)) { "Nie udało się zapisać zdjęcia." }
            return Uri.fromFile(target).toString()
        } finally { temporary.delete() }
    }

    suspend fun add(front: String, side: String, back: String, date: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        PersonalDataGate.mutex.withLock {
            val sources = listOf(front, side, back)
            require(sources.any { it.isNotBlank() }) { "Wybierz przynajmniej jedno zdjęcie." }
            val copies = mutableListOf<String>(); var committed = false
            try {
                for (source in sources) copies += if (source.isBlank()) "" else copyPhoto(source)
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    dao.insertBodyPhoto(BodyPhotoEntity(date = date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")),
                        frontUri = copies[0], sideUri = copies[1], backUri = copies[2]))
                    committed = true
                }
            } finally { if (!committed) copies.filter { it.isNotBlank() }.forEach { deleteOwn(it) } }
        }
    }

    suspend fun replace(id: Long, angle: BodyPhotoAngle, source: String) = withContext(Dispatchers.IO) {
        PersonalDataGate.mutex.withLock {
            val row = dao.bodyPhotoById(id) ?: error("Ten zestaw został usunięty.")
            val copy = copyPhoto(source); var committed = false
            try {
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    check(dao.updateBodyPhoto(row.withPhoto(angle, copy)) == 1) { "Zestaw nie jest już dostępny." }
                    committed = true
                    runCatching { deleteUnreferenced(row.photo(angle)) }
                }
            } finally { if (!committed) deleteOwn(copy) }
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        PersonalDataGate.mutex.withLock {
            val row = dao.bodyPhotoById(id) ?: return@withLock
            withContext(NonCancellable) {
                dao.deleteBodyPhoto(row)
                listOf(row.frontUri, row.sideUri, row.backUri).forEach { runCatching { deleteUnreferenced(it) } }
            }
        }
    }

    /** Preserve unreadable legacy references so the original set/date is never silently lost. */
    suspend fun secureLegacyPhotos() = withContext(Dispatchers.IO) {
        PersonalDataGate.mutex.withLock {
            for (original in dao.getBodyPhotosDirect()) {
                var row = original
                val copies = mutableListOf<String>(); var committed = false
                try {
                    for (angle in BodyPhotoAngle.entries) {
                        val uri = row.photo(angle)
                        if (!uri.startsWith("content://")) continue
                        val copied = try { copyPhoto(uri) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { continue }
                        copies += copied; row = row.withPhoto(angle, copied)
                    }
                    currentCoroutineContext().ensureActive()
                    if (row != original) withContext(NonCancellable) {
                        check(dao.updateBodyPhoto(row) == 1)
                        committed = true
                    }
                } finally { if (!committed) copies.forEach { deleteOwn(it) } }
            }
        }
    }

    private suspend fun deleteUnreferenced(uri: String) {
        if (uri.isBlank()) return
        if (dao.getBodyPhotosDirect().none { uri in listOf(it.frontUri, it.sideUri, it.backUri) }) deleteOwn(uri)
    }
    private fun deleteOwn(uri: String) {
        runCatching {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "file") {
                val file = File(requireNotNull(parsed.path)).canonicalFile
                if (file.parentFile == directory.canonicalFile) file.delete()
            }
        }
    }
}
