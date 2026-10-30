package com.example.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.viewmodel.MealUndoStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndUndoTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var directory: File
    private val recipe = RecipeEntity(id = 11, title = "Owsianka", description = "Porcje: 1", ingredients = "Płatki — 50 g", instructions = "Ugotuj.",
        calories = 200, protein = 8, carbs = 30, fat = 5, category = "Śniadania", isFavorite = true)
    private val meal = MealPlanEntity(id = 21, dayOfWeek = "2026-10-08", mealType = "Śniadanie", recipeId = 11,
        recipeTitle = "Owsianka", calories = 200, isCompleted = true, completedDate = "2026-10-08")
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        directory = File(context.cacheDir, "backup-tests-${java.util.UUID.randomUUID()}").apply { mkdirs() }
    }
    @After fun tearDown() { database.close(); directory.deleteRecursively() }
    private suspend fun seed() {
        val dao = database.dietaDao()
        dao.insertRecipe(recipe); dao.insertMealPlan(meal)
        dao.insertIngredient(RecipeIngredientEntity(id = 31, recipeId = 11, foodName = "Płatki", quantity = 50f, grams = 50f))
        dao.insertIngredient(RecipeIngredientEntity(id = 32, recipeId = -21, foodName = "custom-override", quantity = 20f, grams = 20f))
        dao.saveUserProfile(UserProfileEntity(name = "Kasia", currentWeightKg = 57.8f))
        dao.insertBodyMeasurement(BodyMeasurementEntity(id = 41, date = "2026-10-08", weightKg = 57.8f, waistCm = 72f))
        dao.insertWeightLog(WeightLogEntity(id = 42, date = "2026-10-08", weightKg = 57.8f))
        dao.insertShoppingItem(ShoppingItemEntity(id = 51, itemName = "Płatki", category = "Pantry", isChecked = true, amount = "50 g"))
        dao.insertBeverageLog(BeverageLogEntity(id = 61, date = "2026-10-08", type = "Woda", amountMl = 250))
        dao.insertCreatineLog(CreatineLogEntity(date = "2026-10-08"))
        dao.saveHealthConnectSync(HealthConnectSyncEntity(isConnected = true, stepsToday = 8450))
    }
    @Test fun backupRoundTripPreservesDataAndLocalFilesButNotCredentials() = runBlocking {
        seed()
        val image = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val pdf = File(directory, "document.pdf").apply { writeText("%PDF-1.4 test") }
        val dao = database.dietaDao()
        dao.updateRecipe(recipe.copy(imageUrl = Uri.fromFile(image).toString()))
        dao.insertDocument(DocumentEntity(id = 71, title = "Plan", uriString = Uri.fromFile(pdf).toString(), extractedText = "Zapisany tekst", dateAdded = "2026-10-08"))
        dao.insertBodyPhoto(BodyPhotoEntity(id = 81, date = "2026-10-08", frontUri = Uri.fromFile(image).toString(), sideUri = "", backUri = ""))
        val settings = context.getSharedPreferences("RecipePhotoSettings", Context.MODE_PRIVATE)
        settings.edit().putString("token_pixabay", "DO_NOT_EXPORT_THIS_KEY").putBoolean("enabled", false).commit()
        val original = database.backupDao().snapshot()
        val service = BackupService(context, database)
        val target = File(directory, "export.zip")
        service.export(Uri.fromFile(target))
        ZipFile(target).use { zip -> assertFalse(zip.getInputStream(zip.getEntry("backup.json")).reader().readText().contains("DO_NOT_EXPORT_THIS_KEY")) }
        val prepared = service.prepare(Uri.fromFile(target))
        assertEquals(2, prepared.archive.assets.size)
        dao.updateRecipe(recipe.copy(title = "Późniejsza zmiana", isFavorite = false))
        service.restore(prepared)
        val restored = database.backupDao().snapshot()
        assertEquals(original.meals, restored.meals); assertEquals(original.ingredients, restored.ingredients)
        assertEquals(original.profiles, restored.profiles); assertEquals(original.measurements, restored.measurements)
        assertEquals(original.weights, restored.weights); assertEquals(original.shopping, restored.shopping)
        assertEquals(original.beverages, restored.beverages); assertEquals(original.creatine, restored.creatine)
        assertTrue(restored.recipes.single().isFavorite)
        val restoredImage = File(Uri.parse(restored.recipes.single().imageUrl).path!!)
        assertArrayEquals(image.readBytes(), restoredImage.readBytes())
        assertEquals("Zapisany tekst", restored.documents.single().extractedText)
        assertEquals(pdf.readText(), File(Uri.parse(restored.documents.single().uriString).path!!).readText())
        assertEquals(restored.recipes.single().imageUrl, restored.photos.single().frontUri)
        assertFalse(restored.health.single().isConnected)
        assertEquals("DO_NOT_EXPORT_THIS_KEY", settings.getString("token_pixabay", null))
        assertTrue(service.recoveryFile.isFile)
        val recovery = service.prepare(Uri.fromFile(service.recoveryFile))
        assertEquals("Późniejsza zmiana", recovery.archive.data.recipes.single().title)
        recovery.dispose(); prepared.dispose()
    }
    @Test fun corruptedAttachmentAndUnsafePathsNeverChangeExistingData() = runBlocking {
        seed()
        val image = File(directory, "image.jpg").apply { writeText("original image") }
        database.dietaDao().updateRecipe(recipe.copy(imageUrl = Uri.fromFile(image).toString()))
        val service = BackupService(context, database)
        val target = File(directory, "export.zip"); service.export(Uri.fromFile(target))
        val before = database.backupDao().snapshot()
        val broken = File(directory, "broken.zip")
        ZipFile(target).use { zip -> ZipOutputStream(broken.outputStream()).use { output ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement(); output.putNextEntry(ZipEntry(entry.name))
                if (entry.name.startsWith("assets/")) output.write("corrupted".toByteArray())
                else zip.getInputStream(entry).use { it.copyTo(output) }
                output.closeEntry()
            }
        } }
        try { service.prepare(Uri.fromFile(broken)); fail("Corrupted backup accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(before, database.backupDao().snapshot())
        val unsafe = File(directory, "unsafe.zip")
        ZipOutputStream(unsafe.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("../escape.bin")); zip.write(1); zip.closeEntry() }
        try { service.prepare(Uri.fromFile(unsafe)); fail("Unsafe archive accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(before, database.backupDao().snapshot())
        assertFalse(File(context.cacheDir, "escape.bin").exists())
    }
    @Test fun failedRestoreRollsBackAllTables() = runBlocking {
        seed()
        val before = database.backupDao().snapshot()
        database.dietaDao().insertCache(GeminiCacheEntity(7, "prompt", "old response"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON recipes BEGIN SELECT RAISE(ABORT, 'forced rollback'); END")
        try { database.backupDao().restore(before.copy(recipes = listOf(recipe.copy(title = "new")))); fail("Restore should fail") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, database.backupDao().snapshot())
        assertEquals("old response", database.dietaDao().getCachedResponse(7))
    }
    @Test fun undoRestoresDeletedMealAndIngredientsWithoutClearingFavorites() = runBlocking {
        seed(); val dao = database.dietaDao(); val undo = MealUndoStore(database)
        val originalIngredients = dao.ingredientsForRecipeDirect(-meal.id)
        undo.edit(meal.id, "Usunięto posiłek") { dao.deleteIngredientsForRecipe(-meal.id); dao.deleteMealPlan(meal) }
        assertNull(dao.mealPlanById(meal.id))
        dao.toggleRecipeFavorite(recipe.id)
        assertTrue(undo.undo())
        assertEquals(meal, dao.mealPlanById(meal.id)); assertEquals(originalIngredients, dao.ingredientsForRecipeDirect(-meal.id))
        assertFalse(dao.recipeById(recipe.id)!!.isFavorite)
        assertFalse(undo.undo())
    }
    @Test fun undoRestoresCaloriesAndGramsAndRefusesToOverwriteNewerEdits() = runBlocking {
        seed(); val dao = database.dietaDao(); val undo = MealUndoStore(database)
        val before = dao.ingredientsForRecipeDirect(-21).single()
        undo.edit(meal.id, "Zmieniono gramaturę") { dao.updateIngredient(before.copy(quantity = 40f, grams = 40f)); dao.updateMealPlan(meal.copy(calories = 260)) }
        assertTrue(undo.undo()); assertEquals(before, dao.ingredientsForRecipeDirect(-21).single()); assertEquals(200, dao.mealPlanById(21)!!.calories)
        undo.edit(meal.id, "Zmieniono porcje") { dao.updateMealPlan(meal.copy(servings = 2f, calories = 400)) }
        dao.updateMealPlan(meal.copy(servings = 3f, calories = 600))
        try { undo.undo(); fail("Newer edit overwritten") } catch (_: IllegalArgumentException) { }
        assertEquals(600, dao.mealPlanById(21)!!.calories)
    }
    @Test fun failedMealEditIsAtomicAndDoesNotCreateUndoAction() = runBlocking {
        seed(); val dao = database.dietaDao(); val undo = MealUndoStore(database)
        try { undo.edit(meal.id, "Nieudana zmiana") { dao.deleteIngredientsForRecipe(-21); dao.updateMealPlan(meal.copy(calories = 0)); error("forced failure") }; fail("Edit should fail") }
        catch (_: IllegalStateException) { }
        assertEquals(meal, dao.mealPlanById(21)); assertEquals(1, dao.ingredientsForRecipeDirect(-21).size)
        assertNull(undo.label.value)
    }
    @Test fun favoriteToggleChangesOnlyFavoriteFieldAndSurvivesReload() = runBlocking {
        seed(); val dao = database.dietaDao()
        dao.toggleRecipeFavorite(11); assertEquals(recipe.copy(isFavorite = false), dao.recipeById(11))
        dao.toggleRecipeFavorite(11); assertEquals(recipe, database.backupDao().snapshot().recipes.single())
    }
    @Test fun incompatibleVersionAndMissingRecipeAreRejectedBeforeRestore() = runBlocking {
        seed()
        val service = BackupService(context, database)
        val target = File(directory, "valid.zip"); service.export(Uri.fromFile(target))
        val prepared = service.prepare(Uri.fromFile(target)); val archive = prepared.archive
        try { BackupService.validate(archive.copy(version = 99)); fail("Unsupported version accepted") } catch (_: IllegalArgumentException) { }
        try { BackupService.validate(archive.copy(data = archive.data.copy(recipes = emptyList()))); fail("Orphan meal accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(recipe, database.dietaDao().recipeById(recipe.id)); prepared.dispose()
    }
}
