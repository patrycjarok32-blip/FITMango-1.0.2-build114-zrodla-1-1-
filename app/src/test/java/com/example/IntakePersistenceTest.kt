package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntakePersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun drinksAndCreatinePersistAndMistakesCanBeRemoved(): Unit = runBlocking {
        val name = "intake-restart.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            val repo = DietaRepository(db.dietaDao())
            val old = repo.insertBeverageLog(BeverageLogEntity(date = "2026-10-05", type = "Woda", amountMl = 100))
            val zero = repo.insertBeverageLog(BeverageLogEntity(date = "2026-10-06", type = "Napój zero", amountMl = 330))
            repo.insertBeverageLog(BeverageLogEntity(date = "2026-10-06", type = "Zioła / napar", amountMl = 175))
            repo.markCreatineTaken("2026-10-06")
            repo.markCreatineTaken("2026-10-06")
            assertEquals(1, repo.creatineLogs.first().size)
            db.close()
            db = open()
            val reopened = DietaRepository(db.dietaDao())
            assertEquals(505, reopened.beverageLogs.first().filter { it.date == "2026-10-06" }.sumOf { it.amountMl })
            assertEquals(5000, reopened.creatineLogs.first().single().amountMg)
            reopened.deleteBeverageLog(zero)
            assertEquals(175, reopened.beverageLogs.first().filter { it.date == "2026-10-06" }.sumOf { it.amountMl })
            assertTrue(reopened.beverageLogs.first().any { it.id == old })
            reopened.deleteCreatineLog("2026-10-06")
            assertTrue(reopened.creatineLogs.first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun invalidDrinkInputDoesNotCreateEntries(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = DietaRepository(db.dietaDao())
            for (amount in listOf(-1, 0, 10001)) {
                assertTrue(runCatching { repo.insertBeverageLog(BeverageLogEntity(date = "2026-10-06", type = "Woda", amountMl = amount)) }.isFailure)
            }
            assertTrue(runCatching { repo.insertBeverageLog(BeverageLogEntity(date = "2026-10-06", type = "", amountMl = 250)) }.isFailure)
            assertTrue(repo.beverageLogs.first().isEmpty())
        } finally { db.close() }
    }

    @Test fun migrationSixToSevenPreservesExistingData(): Unit = runBlocking {
        val name = "migration-six-seven.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val dao = db.dietaDao()
            val id = dao.insertRecipe(RecipeEntity(title = "Zachowany przepis", description = "Autor zdjęcia", ingredients = "Ryż", instructions = "Ugotuj",
                calories = 100, protein = 1, carbs = 20, fat = 0, category = "Dinner", imageUrl = "/saved/photo.jpg"))
            dao.saveUserProfile(UserProfileEntity(name = "Zachowany profil", targetCalories = 1900))
            dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Kolacja", recipeId = id, recipeTitle = "Zachowany przepis", calories = 100))
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "Ryż", category = "Zboża", isChecked = true))
            db.close()
            // Version 6 has precisely the same existing tables; remove only the two new tables
            // to create a version 6 schema fixture, then run the actual Room migration/validation.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("ALTER TABLE shopping_items DROP COLUMN weekStart")
                it.execSQL("ALTER TABLE shopping_items DROP COLUMN isManual")
                it.execSQL("ALTER TABLE meal_plans DROP COLUMN completedDate")
                it.execSQL("DROP TABLE beverage_logs")
                it.execSQL("DROP TABLE creatine_logs")
                it.version = 6
            }
            db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9).build()
            val migrated = db.dietaDao()
            assertEquals(id, migrated.getAllRecipes().first().single().id)
            assertEquals("/saved/photo.jpg", migrated.getAllRecipes().first().single().imageUrl)
            assertEquals(1900, migrated.getUserProfile().first()!!.targetCalories)
            assertEquals(1, migrated.getAllMealPlans().first().size)
            assertTrue(migrated.getAllShoppingItems().first().single().isChecked)
            assertTrue(migrated.getBeverageLogs().first().isEmpty())
            assertTrue(migrated.getCreatineLogs().first().isEmpty())
            migrated.insertBeverageLog(BeverageLogEntity(date = "2026-10-06", type = "Woda", amountMl = 250))
            assertEquals(250, migrated.getBeverageLogs().first().single().amountMl)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun migrationSevenToEightKeepsDataWithoutInventingConsumptionDates(): Unit = runBlocking {
        val name = "migration-seven-eight.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val dao = db.dietaDao()
            val id = dao.insertRecipe(RecipeEntity(title = "Omlet", description = "", ingredients = "Jajka", instructions = "Usmaż", calories = 200, protein = 15, carbs = 2, fat = 13, category = "Śniadanie"))
            dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Środa", mealType = "Śniadanie", recipeId = id, recipeTitle = "Omlet", calories = 200, isCompleted = true))
            dao.insertBeverageLog(BeverageLogEntity(date = "2026-10-07", type = "Woda", amountMl = 250))
            dao.insertCreatineLog(CreatineLogEntity(date = "2026-10-07"))
            db.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("ALTER TABLE shopping_items DROP COLUMN weekStart")
                it.execSQL("ALTER TABLE shopping_items DROP COLUMN isManual")
                it.execSQL("ALTER TABLE meal_plans DROP COLUMN completedDate"); it.version = 7
            }
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9).build()
            val migrated = db.dietaDao()
            val meal = migrated.getAllMealPlans().first().single()
            assertTrue(meal.isCompleted); assertNull(meal.completedDate)
            assertEquals(id, migrated.getAllRecipes().first().single().id)
            assertEquals(250, migrated.getBeverageLogs().first().single().amountMl)
            assertEquals(5000, migrated.getCreatineLogs().first().single().amountMg)
        } finally { db.close(); context.deleteDatabase(name) }
    }

}
