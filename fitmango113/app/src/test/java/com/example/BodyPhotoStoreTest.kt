package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupService
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyPhotoStoreTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var store: BodyPhotoStore
    private lateinit var directory: File
    private val dao get() = database.dietaDao()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = BodyPhotoStore(context, dao)
        directory = File(context.cacheDir, "nutrition_labels/body-tests-${java.util.UUID.randomUUID()}").apply { mkdirs() }
    }
    @After fun cleanup() {
        database.close(); directory.deleteRecursively()
        File(context.filesDir, "body-photos").deleteRecursively()
    }
    private fun image(name: String = "source.png"): File = File(directory, name).also { file ->
        val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
    }
    private fun File.uri() = Uri.fromFile(this).toString()
    private fun saved(uri: String) = File(requireNotNull(Uri.parse(uri).path))

    @Test fun newSetCopiesBytesAndKeepsWorkingWhenTheOriginalDisappears() = runBlocking {
        val source = image(); val bytes = source.readBytes()
        store.add(source.uri(), "", "", LocalDate.parse("2026-10-24"))
        val row = dao.getBodyPhotosDirect().single()
        assertEquals("24.10.2026", row.date)
        assertNotEquals(source.uri(), row.frontUri)
        assertEquals("", row.sideUri); assertEquals("", row.backUri)
        source.delete()
        assertArrayEquals(bytes, saved(row.frontUri).readBytes())
    }
    @Test fun partialFailureRollsBackAllCopiesAndNeverCreatesAnEmptyGallery() = runBlocking {
        val source = image()
        val broken = File(directory, "not-an-image.txt").apply { writeText("not an image") }
        assertTrue(runCatching { store.add(source.uri(), broken.uri(), "") }.isFailure)
        assertTrue(dao.getBodyPhotosDirect().isEmpty())
        assertTrue(File(context.filesDir, "body-photos").listFiles().orEmpty().isEmpty())
        assertTrue(runCatching { store.add("", "", "") }.isFailure)
    }
    @Test fun replacementPreservesDateAndOtherAnglesAndFailurePreservesTheOldImage() = runBlocking {
        val source = image(); store.add(source.uri(), source.uri(), source.uri(), LocalDate.parse("2026-10-24"))
        val old = dao.getBodyPhotosDirect().single()
        assertTrue(runCatching { store.replace(old.id, BodyPhotoAngle.SIDE, File(directory, "missing").uri()) }.isFailure)
        assertEquals(old, dao.bodyPhotoById(old.id))
        store.replace(old.id, BodyPhotoAngle.SIDE, source.uri())
        val updated = requireNotNull(dao.bodyPhotoById(old.id))
        assertEquals(old.date, updated.date); assertEquals(old.frontUri, updated.frontUri); assertEquals(old.backUri, updated.backUri)
        assertNotEquals(old.sideUri, updated.sideUri)
        assertTrue(saved(updated.sideUri).isFile); assertFalse(saved(old.sideUri).exists())
    }
    @Test fun legacyMigrationCopiesAvailableContentUrisAndKeepsUnavailableReferencesAndDates() = runBlocking {
        val source = image(); val bytes = source.readBytes()
        val front = FileProvider.getUriForFile(context, "${context.packageName}.nutrition_labels", source).toString()
        val missing = FileProvider.getUriForFile(context, "${context.packageName}.nutrition_labels", File(directory, "gone.png")).toString()
        dao.insertBodyPhoto(BodyPhotoEntity(id = 24, date = "24.10.2026", frontUri = front, sideUri = missing, backUri = ""))
        store.secureLegacyPhotos()
        val row = requireNotNull(dao.bodyPhotoById(24))
        assertEquals("24.10.2026", row.date); assertEquals(missing, row.sideUri); assertEquals("", row.backUri)
        assertTrue(row.frontUri.startsWith("file://")); source.delete()
        assertArrayEquals(bytes, saved(row.frontUri).readBytes())
        store.secureLegacyPhotos()
        assertEquals(row, dao.bodyPhotoById(24))
    }
    @Test fun deletingASetPreservesSharedReferencesAndTheOriginalPhoneFile() = runBlocking {
        val source = image(); store.add(source.uri(), "", "")
        val first = dao.getBodyPhotosDirect().single()
        dao.insertBodyPhoto(first.copy(id = first.id + 1))
        store.delete(first.id)
        assertTrue(saved(first.frontUri).exists())
        store.delete(first.id + 1)
        assertFalse(saved(first.frontUri).exists())
        assertTrue(source.exists())
    }
    @Test fun privateBodyPhotosSurviveTheExistingBackupRoundTrip() = runBlocking {
        val source = image(); val bytes = source.readBytes(); store.add(source.uri(), "", "")
        val row = dao.getBodyPhotosDirect().single(); source.delete()
        val service = BackupService(context, database)
        val target = File(directory, "backup.zip")
        service.export(Uri.fromFile(target))
        val prepared = service.prepare(Uri.fromFile(target))
        try {
            assertTrue(prepared.archive.missingFiles.isEmpty())
            assertTrue(row.frontUri in prepared.archive.assets)
            store.delete(row.id)
            service.restore(prepared)
            val restored = dao.getBodyPhotosDirect().single()
            assertEquals(row.date, restored.date)
            assertArrayEquals(bytes, saved(restored.frontUri).readBytes())
        } finally { prepared.dispose() }
    }
}
