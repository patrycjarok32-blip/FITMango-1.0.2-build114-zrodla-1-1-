package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.viewmodel.HabitsDiary
import com.example.data.AppDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Credentials, permission grants and AI response caches deliberately do not travel.
data class BackupAsset(val name: String, val size: Long, val sha256: String)
data class BackupArchive(
    val format: String, val version: Int, val schema: Int, val createdAt: String,
    val data: BackupData, val photosEnabled: Boolean,
    val assets: Map<String, BackupAsset>, val missingFiles: List<String>,
    val habitsDiary: String = "",
    val mealSets: String = "[]",
    val reminders: String = "",
    val usability: String = "",
    val geminiKey: String = "", val pixabayKey: String = "", val photoAiKey: String = ""
)
data class PreparedBackup(val archive: BackupArchive, val directory: File) {
    fun dispose() { directory.deleteRecursively() }
}

class BackupService(private val context: Context, private val database: AppDatabase) {
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(BackupArchive::class.java)
    val recoveryFile: File get() = File(context.filesDir, "fitmango-before-restore.zip")

    val automaticFile: File get() = File(context.filesDir, "fitmango-automatic.zip")
    suspend fun saveAutomatic(): Int = withContext(Dispatchers.IO) {
        val temp = File(context.filesDir, "fitmango-automatic.tmp")
        try {
            val missing = temp.outputStream().use { writeArchive(it) }
            require(temp.renameTo(automaticFile)) { "Nie udało się zachować kopii lokalnej." }
            missing
        } finally { temp.delete() }
    }
    suspend fun exportAutomatic(uri: Uri) = withContext(Dispatchers.IO) {
        require(automaticFile.isFile && automaticFile.length() > 0L) { "Brak prawidłowej kopii automatycznej." }
        val expected = automaticFile.length()
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            automaticFile.inputStream().buffered().use { input ->
                val copied = input.copyTo(output, 64 * 1024)
                require(copied == expected) { "Niepełny eksport kopii lokalnej." }
            }
            output.flush()
        } ?: error("Nie można zapisać pliku.")
        verifyExportedFile(uri, expected, encrypted = false)
    }

    suspend fun export(uri: Uri, password: String): String = withContext(Dispatchers.IO) {
        require(password.length >= 8) { "Hasło kopii musi mieć co najmniej 8 znaków." }
        val temp = File.createTempFile("fitmango-backup-", ".zip", context.cacheDir)
        try {
            val missing = temp.outputStream().use { writeArchive(it, includeKeys = true) }
            require(temp.length() > 0L) { "Nie udało się przygotować danych kopii." }
            // Encrypt to disk in a stream. Reading a 200+ MB ZIP and its AES-GCM
            // output into RAM can exhaust the Android heap and leave a 0-byte file.
            val encryptedFile = File.createTempFile("fitmango-encrypted-", ".bin", context.cacheDir)
            try {
                encryptedFile.outputStream().buffered().use { output ->
                    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
                    output.write(MAGIC)
                    output.write(salt)
                    output.write(nonce)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, derivedKey(password, salt), GCMParameterSpec(128, nonce))
                    javax.crypto.CipherOutputStream(output, cipher).use { encryptedOutput ->
                        temp.inputStream().buffered().use { input -> input.copyTo(encryptedOutput, 64 * 1024) }
                    }
                }
                val expectedSize = encryptedFile.length()
                require(expectedSize > 48L) { "Zaszyfrowana kopia jest pusta." }
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    encryptedFile.inputStream().buffered().use { input -> input.copyTo(output, 64 * 1024) }
                    output.flush()
                } ?: error("Nie można zapisać pliku w wybranym miejscu.")
                verifyExportedFile(uri, expectedSize, encrypted = true)
            } catch (e: Exception) {
                // Keep the failed file for diagnosis: deleting it can hide the actual error.
                throw IllegalStateException("Nie udało się zapisać pełnej kopii: ${e.message}", e)
            } finally {
                encryptedFile.delete()
            }
            if (missing == 0) "Kopia zapisana i sprawdzona (${context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1} bajtów). Przechowuj ją w bezpiecznym miejscu."
            else "Kopia zapisana i sprawdzona (${context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1} bajtów). Niedostępne pliki: $missing. Ich tekst i pozostałe dane zachowano."
        } finally { temp.delete() }
    }

    /** Verify the actual exported bytes even when the document provider reports an unknown size. */
    private fun verifyExportedFile(uri: Uri, expected: Long, encrypted: Boolean) {
        require(expected > 0L) { "Nie można potwierdzić pustej kopii." }
        val reported = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        if (reported != null && reported >= 0L) {
            require(reported == expected) {
                "Niepełna kopia: zapisano $reported z $expected bajtów."
            }
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Nie można ponownie odczytać zapisanej kopii.")
        input.buffered().use { stream ->
            val header = ByteArray(4)
            var offset = 0
            while (offset < header.size) {
                val read = stream.read(header, offset, header.size - offset)
                require(read > 0) { "Zapisany plik jest pusty lub niepełny." }
                offset += read
            }
            require(header.contentEquals(if (encrypted) MAGIC else byteArrayOf(80, 75, 3, 4))) {
                "Zapisany plik nie ma prawidłowego nagłówka kopii."
            }
            var count = header.size.toLong()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                count += n
                require(count <= expected) { "Zapisany plik ma nieoczekiwany rozmiar." }
            }
            require(count == expected) {
                "Niepełna kopia: odczytano $count z $expected bajtów."
            }
        }
    }

    // Also used before a restore, so the previous data can be recovered locally.
    internal suspend fun writeArchive(output: OutputStream, includeKeys: Boolean = false): Int {
        val data = database.backupDao().snapshot()
        val uris = (data.recipes.map { it.imageUrl } + data.documents.map { it.uriString } +
            data.photos.flatMap { listOf(it.frontUri, it.sideUri, it.backUri) })
            .filter { it.startsWith("content://") || it.startsWith("file://") && !it.startsWith("file:///android_asset/") ||
                it.startsWith(context.filesDir.absolutePath + "/") ||
                it.startsWith("https://cdn.pixabay.com/") || it.startsWith("https://pixabay.com/") }
            .distinct()
        require(uris.size < 20_000) { "Za dużo zdjęć i dokumentów do jednej kopii." }
        val assets = linkedMapOf<String, BackupAsset>()
        val missing = mutableListOf<String>()
        var total = 0L
        ZipOutputStream(output).use { zip ->
            for ((index, source) in uris.withIndex()) {
                val staged = File.createTempFile("backup-asset-", ".bin", context.cacheDir)
                try {
                    try {
                        if (source.startsWith("https://cdn.pixabay.com/") || source.startsWith("https://pixabay.com/")) {
                            // Trusted Pixabay image only; bounded download, no credentials in URL.
                            val bytes = com.example.viewmodel.RecipePhotoService().download(
                                com.example.viewmodel.AutomaticPhoto(source, "", false))
                            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            require(options.outWidth in 1..4096 && options.outHeight in 1..4096 &&
                                options.outMimeType in listOf("image/jpeg", "image/png", "image/webp"))
                            staged.writeBytes(bytes)
                        } else {
                            val input = if (source.startsWith(context.filesDir.absolutePath + "/")) File(source).inputStream()
                                else if (source.startsWith("file://")) File(Uri.parse(source).path ?: "").inputStream()
                                else context.contentResolver.openInputStream(Uri.parse(source)) ?: error("Niedostępny plik")
                            input.use { stream -> staged.outputStream().use { copyLimited(stream, it, MAX_ASSET) } }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { missing.add(source); continue }
                    total += staged.length()
                    require(total <= MAX_TOTAL) { "Zdjęcia i dokumenty przekraczają limit kopii 256 MB." }
                    val name = "assets/$index.bin"
                    assets[source] = BackupAsset(name, staged.length(), digest(staged))
                    zip.putNextEntry(ZipEntry(name))
                    staged.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                } finally { staged.delete() }
            }
            val archive = BackupArchive("FITMango", 1, 9, java.time.Instant.now().toString(), data,
                context.getSharedPreferences("RecipePhotoSettings", Context.MODE_PRIVATE).getBoolean("enabled", true), assets, missing, HabitsDiary.export(context), com.example.viewmodel.MealConvenienceSupport.export(context), com.example.reminders.ReminderSupport.export(context), com.example.viewmodel.UsabilityPreferences.export(context),
                if (includeKeys) com.example.ai.SecureKeyStorage.getKey().orEmpty() else "",
                if (includeKeys) com.example.viewmodel.RecipePhotoSettings(context).token("pixabay") else "",
                if (includeKeys) com.example.viewmodel.RecipePhotoSettings(context).token("hf") else "")
            validate(archive)
            val json = adapter.toJson(archive).toByteArray(Charsets.UTF_8)
            require(json.size <= MAX_MANIFEST && total + json.size <= MAX_TOTAL) { "Dane przekraczają limit kopii." }
            zip.putNextEntry(ZipEntry("backup.json")); zip.write(json); zip.closeEntry()
        }
        return missing.size
    }

    suspend fun prepare(uri: Uri, password: String = ""): PreparedBackup = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "backup-preview-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var total = 0L
            val names = mutableSetOf<String>()
            // Stage the archive on disk instead of allocating a 200+ MB ByteArray.
            // For encrypted backups, fully consume the cipher stream so AES-GCM
            // authentication is checked before any restored data is accepted.
            val stagedZip = File(directory, "staged-backup.zip")
            context.contentResolver.openInputStream(uri)?.buffered()?.use { source ->
                val header = ByteArray(4)
                var offset = 0
                while (offset < header.size) {
                    val count = source.read(header, offset, header.size - offset)
                    if (count < 0) break
                    offset += count
                }
                require(offset == 4) { "Plik kopii jest pusty lub uszkodzony." }
                val stream: InputStream = if (header.contentEquals(MAGIC)) {
                    require(password.isNotEmpty()) { "Podaj hasło zaszyfrowanej kopii." }
                    val salt = ByteArray(16)
                    val nonce = ByteArray(12)
                    java.io.DataInputStream(source).apply {
                        readFully(salt)
                        readFully(nonce)
                    }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, derivedKey(password, salt), GCMParameterSpec(128, nonce))
                    javax.crypto.CipherInputStream(source, cipher)
                } else {
                    java.io.SequenceInputStream(ByteArrayInputStream(header), source)
                }
                stream.use { input ->
                    stagedZip.outputStream().buffered().use { output ->
                        copyLimited(input, output, MAX_TOTAL + 1024L)
                    }
                }
            } ?: error("Nie można otworzyć kopii.")
            val input = stagedZip.inputStream().buffered()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && (entry.name == "backup.json" || ASSET_NAME.matches(entry.name))) { "Nieprawidłowa zawartość kopii." }
                    require(names.add(entry.name) && names.size <= 20_000) { "Powtórzone pliki lub zbyt duża kopia." }
                    val file = File(directory, entry.name)
                    file.parentFile?.mkdirs()
                    val limit = if (entry.name == "backup.json") MAX_MANIFEST.toLong() else MAX_ASSET
                    val size = file.outputStream().use { copyLimited(zip, it, minOf(limit, MAX_TOTAL - total)) }
                    total += size
                    zip.closeEntry()
                }
            }
            stagedZip.delete()
            val manifest = File(directory, "backup.json")
            require(manifest.isFile) { "Brak danych FITMango w pliku." }
            val archive = adapter.fromJson(manifest.readText()) ?: error("Nieprawidłowe dane kopii.")
            validate(archive)
            val declaredNames = archive.assets.values.map { it.name }
            require(declaredNames.distinct().size == declaredNames.size && names == declaredNames.toSet() + "backup.json") { "Niezgodna lista plików w kopii." }
            archive.assets.values.forEach { asset ->
                require(ASSET_NAME.matches(asset.name)) { "Nieprawidłowa ścieżka zdjęcia." }
                val file = File(directory, asset.name)
                require(file.isFile && file.length() == asset.size && digest(file) == asset.sha256) { "Kopia jest uszkodzona. Obecne dane pozostają bez zmian." }
            }
            PreparedBackup(archive, directory)
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }

    // Call while Health Connect / photo writers are paused by the coordinator.
    suspend fun restore(prepared: PreparedBackup): String = withContext(Dispatchers.IO) {
        validate(prepared.archive)
        val previous = File.createTempFile("previous-data-", ".zip", context.filesDir)
        val target = File(context.filesDir, "restored-files/${UUID.randomUUID()}")
        var committed = false
        try {
            previous.outputStream().use { writeArchive(it) }
            // Preserve the previous recovery file unless the new one is ready.
            if (!previous.renameTo(recoveryFile)) {
                previous.copyTo(recoveryFile, overwrite = true); previous.delete()
            }
            val replacements = prepared.archive.assets.mapValues { (_, asset) ->
                val from = File(prepared.directory, asset.name)
                require(from.length() == asset.size && digest(from) == asset.sha256) { "Uszkodzony załącznik w kopii." }
                val to = File(target, asset.name).apply { parentFile?.mkdirs() }
                from.copyTo(to)
                Uri.fromFile(to).toString()
            }
            fun restoredUri(source: String): String = replacements[source] ?: when {
                source.startsWith("https://") || source.startsWith("http://") || source.startsWith("file:///android_asset/") -> source
                else -> ""
            }
            val original = prepared.archive.data
            val data = original.copy(
                recipes = original.recipes.map { it.copy(imageUrl = restoredUri(it.imageUrl)) },
                documents = original.documents.map { it.copy(uriString = replacements[it.uriString].orEmpty()) },
                photos = original.photos.map { it.copy(frontUri = restoredUri(it.frontUri), sideUri = restoredUri(it.sideUri), backUri = restoredUri(it.backUri)) },
                health = original.health.map { it.copy(isConnected = false, syncStatusMessage = "Przywrócono kopię. Połącz ponownie z Health Connect.") }
            )
            // A cancellation after COMMIT must not delete attachments referenced by Room.
            withContext(NonCancellable) {
                database.backupDao().restore(data)
                committed = true
                context.getSharedPreferences("RecipePhotoSettings", Context.MODE_PRIVATE).edit()
                    .putBoolean("enabled", prepared.archive.photosEnabled).apply()
                com.example.viewmodel.UsabilityPreferences.restore(context, prepared.archive.usability)
                HabitsDiary.restore(context, prepared.archive.habitsDiary)
                com.example.viewmodel.MealConvenienceSupport.restore(context, prepared.archive.mealSets)
                com.example.reminders.ReminderSupport.save(context, com.example.reminders.ReminderSupport.decode(prepared.archive.reminders))
                if (prepared.archive.geminiKey.isNotBlank()) com.example.ai.SecureKeyStorage.saveKey(prepared.archive.geminiKey)
                val photoSettings = com.example.viewmodel.RecipePhotoSettings(context)
                if (prepared.archive.pixabayKey.isNotBlank()) photoSettings.saveToken("pixabay", prepared.archive.pixabayKey)
                if (prepared.archive.photoAiKey.isNotBlank()) photoSettings.saveToken("hf", prepared.archive.photoAiKey)
            }
            "Przywrócono dane, zdjęcia i klucze API zawarte w kopii. Health Connect połącz ponownie w Profilu."
        } finally {
            previous.delete()
            if (!committed) target.deleteRecursively()
        }
    }

    suspend fun exportRecovery(uri: Uri) = withContext(Dispatchers.IO) {
        require(recoveryFile.isFile) { "Brak kopii sprzed przywrócenia." }
        context.contentResolver.openOutputStream(uri, "wt")?.use { output -> recoveryFile.inputStream().use { it.copyTo(output) } }
            ?: error("Nie można zapisać pliku.")
    }

    companion object {
        private val MAGIC = byteArrayOf(70, 77, 66, 50)
        private fun derivedKey(password: String, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
            return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
            finally { spec.clearPassword() }
        }
        private fun encryptArchive(plain: ByteArray, password: String): ByteArray {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey(password, salt), GCMParameterSpec(128, nonce))
            return MAGIC + salt + nonce + cipher.doFinal(plain)
        }
        private fun decryptArchive(bytes: ByteArray, password: String): ByteArray {
            require(password.isNotEmpty()) { "Podaj hasło zaszyfrowanej kopii." }
            require(bytes.size >= 48) { "Uszkodzona kopia." }
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, derivedKey(password, bytes.copyOfRange(4, 20)), GCMParameterSpec(128, bytes.copyOfRange(20, 32)))
                cipher.doFinal(bytes.copyOfRange(32, bytes.size))
            } catch (_: javax.crypto.AEADBadTagException) { error("Nieprawidłowe hasło lub uszkodzona kopia.") }
        }
        private const val MAX_MANIFEST = 16 * 1024 * 1024
        private const val MAX_ASSET = 64L * 1024 * 1024
        private const val MAX_TOTAL = 256L * 1024 * 1024
        private val ASSET_NAME = Regex("assets/[0-9]{1,8}\\.bin")
        private fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) {
                val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n)
            } }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
            var total = 0L; val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) return total
                total += n; require(total <= limit) { "Plik kopii przekracza dozwolony rozmiar." }; output.write(buffer, 0, n)
            }
        }
        internal fun validate(archive: BackupArchive) {
            HabitsDiary.validate(archive.habitsDiary)
            com.example.viewmodel.MealConvenienceSupport.decode(archive.mealSets)
            com.example.viewmodel.UsabilityPreferences.validate(archive.usability)
            com.example.reminders.ReminderSupport.decode(archive.reminders)
            require(archive.format == "FITMango" && archive.version == 1 && archive.schema in 8..9) { "Nieobsługiwana wersja kopii FITMango." }
            java.time.Instant.parse(archive.createdAt)
            val d = archive.data
            fun ids(values: List<Long>) { require(values.size <= 100_000 && values.all { it in 1 until Long.MAX_VALUE } && values.distinct().size == values.size) { "Nieprawidłowe identyfikatory danych." } }
            ids(d.recipes.map { it.id }); ids(d.ingredients.map { it.id }); ids(d.meals.map { it.id })
            d.shopping.forEach { item ->
                if (item.weekStart.isNotBlank()) com.example.viewmodel.ShoppingWeekSupport.requireWeek(item.weekStart)
            }
            ids(d.shopping.map { it.id }); ids(d.weights.map { it.id }); ids(d.measurements.map { it.id })
            ids(d.photos.map { it.id }); ids(d.documents.map { it.id }); ids(d.beverages.map { it.id })
            require(d.profiles.size <= 1 && d.profiles.all { it.id == 1 } && d.health.size <= 1 && d.health.all { it.id == 1 })
            require(d.creatine.size <= 100_000 && d.creatine.map { it.date }.distinct().size == d.creatine.size)
            val recipeIds = d.recipes.map { it.id }.toSet(); val mealIds = d.meals.map { it.id }.toSet()
            require(d.meals.all { it.recipeId == 0L || it.recipeId in recipeIds }) { "Brak przepisu dla posiłku w kopii." }
            require(d.ingredients.all { it.recipeId in recipeIds || it.recipeId < 0 && -it.recipeId in mealIds }) { "Brak przepisu lub posiłku dla składnika." }
            require(d.recipes.all { it.title.isNotBlank() && it.calories >= 0 && it.protein >= 0 && it.carbs >= 0 && it.fat >= 0 })
            require(d.meals.all { it.servings.isFinite() && it.servings > 0 && it.calories >= 0 })
            require(d.ingredients.all { row -> listOf(row.quantity, row.grams, row.caloriesPer100g, row.proteinPer100g, row.carbsPer100g, row.fatPer100g).all { it.isFinite() } })
            require(d.measurements.all { it.weightKg.isFinite() } && d.weights.all { it.weightKg.isFinite() })
            require(d.profiles.all { it.heightCm.isFinite() && it.currentWeightKg.isFinite() })
            require(d.health.all { it.latestWeightKg.isFinite() && it.totalCaloriesBurned.isFinite() && it.activeCaloriesBurned.isFinite() })
        }
    }
}
