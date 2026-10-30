package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import com.example.data.*
import com.example.viewmodel.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeeklyShoppingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun datesCrossWeekAndYearBoundariesWithoutReusingWeekdays() {
        fun meal(day: String) = MealPlanEntity(dayOfWeek = day, mealType = "Obiad", recipeTitle = "Test", calories = 100)
        assertEquals("2026-10-12", ShoppingWeekSupport.weekOf(meal("2026-10-18")))
        assertEquals("2026-10-19", ShoppingWeekSupport.weekOf(meal("2026-10-19")))
        assertEquals("2026-12-28", ShoppingWeekSupport.weekOf(meal("2027-01-01")))
        assertNull(ShoppingWeekSupport.weekOf(meal("Poniedziałek")))
        assertEquals(LocalDate.parse("2026-10-12"), ShoppingWeekSupport.start(LocalDate.parse("2026-10-18")))
    }

    @Test fun generationSeparatesWeeksPreservesManualRowsAndCheckedStateAndSortsOwnedLast() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = AppDatabase.getDatabase(app).dietaDao()
        runBlocking {
            dao.saveUserProfile(UserProfileEntity())
            val recipe = dao.insertRecipe(RecipeEntity(title = "Owsianka", description = "", ingredients = "Płatki owsiane — 100 g", instructions = "Wymieszaj", calories = 370, protein = 10, carbs = 60, fat = 8, category = "Śniadanie"))
            dao.insertMealPlan(MealPlanEntity(dayOfWeek = "2026-10-12", mealType = "Śniadanie", recipeId = recipe, recipeTitle = "Owsianka", calories = 370))
            dao.insertMealPlan(MealPlanEntity(dayOfWeek = "2026-10-19", mealType = "Śniadanie", recipeId = recipe, recipeTitle = "Owsianka", calories = 740, servings = 2f))
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "Stara lista", category = "Inne", isChecked = true))
            dao.insertShoppingItem(ShoppingItemEntity(itemName = "Jabłko", category = "Warzywa i owoce", weekStart = "2026-10-12", isManual = true))
        }
        val vm = DietaViewModel(app)
        compose.setContent { Text("Zakupy") }
        fun rows() = runBlocking { dao.getAllShoppingItems().first() }
        fun waitFor(check: () -> Boolean) = compose.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); check() }
        vm.generateShoppingList("2026-10-12")
        waitFor { rows().any { it.weekStart == "2026-10-12" && !it.isManual } }
        val first = rows().single { it.weekStart == "2026-10-12" && !it.isManual }
        assertEquals("100 g", first.amount)
        vm.toggleShoppingItem(first)
        waitFor { rows().any { it.id == first.id && it.isChecked } }
        vm.generateShoppingList("2026-10-19")
        waitFor { rows().any { it.weekStart == "2026-10-19" } }
        val next = rows().single { it.weekStart == "2026-10-19" }
        assertEquals("200 g", next.amount); assertFalse(next.isChecked)
        vm.generateShoppingList("2026-10-12")
        // Adding the manual product queues behind generation on the same mutex.
        vm.addShoppingItem("Mleko", "Nabiał", "2026-10-12")
        waitFor { rows().any { it.itemName == "Mleko" } }
        assertTrue(rows().single { it.id == first.id }.isChecked)
        val visible = ShoppingWeekSupport.visible(rows(), "2026-10-12", "Wszystkie")
        assertEquals(3, visible.size)
        assertTrue(visible.last().isChecked)
        assertEquals("Mleko", ShoppingWeekSupport.visible(rows(), "2026-10-12", "Nabiał").single().itemName)
        assertTrue(rows().single { it.itemName == "Stara lista" }.isChecked)
        assertEquals(1, rows().count { it.weekStart == "2026-10-19" })
        // Room persistence, rather than only a UI state: a new DAO read keeps ownership.
        assertTrue(AppDatabase.getDatabase(app).dietaDao().let { runBlocking { it.getAllShoppingItems().first() } }.single { it.id == first.id }.isChecked)
    }

    @Test fun migrationPreservesOldGlobalListWithoutAssigningItToToday() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "shopping-migration-8-9.db"
        app.deleteDatabase(name)
        var db = Room.databaseBuilder(app, AppDatabase::class.java, name).build()
        try {
            db.dietaDao().insertShoppingItem(ShoppingItemEntity(itemName = "Ryż", category = "Inne", isChecked = true, amount = "200 g"))
            db.close()
            SQLiteDatabase.openDatabase(app.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("CREATE TABLE shopping_items_v8 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemName TEXT NOT NULL, category TEXT NOT NULL, isChecked INTEGER NOT NULL, amount TEXT NOT NULL)")
                it.execSQL("INSERT INTO shopping_items_v8 (id,itemName,category,isChecked,amount) SELECT id,itemName,category,isChecked,amount FROM shopping_items")
                it.execSQL("DROP TABLE shopping_items")
                it.execSQL("ALTER TABLE shopping_items_v8 RENAME TO shopping_items")
                it.version = 8
            }
            db = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_8_9).build()
            val row = db.dietaDao().getAllShoppingItems().first().single()
            assertEquals("", row.weekStart); assertFalse(row.isManual)
            assertTrue(row.isChecked); assertEquals("200 g", row.amount)
        } finally { db.close(); app.deleteDatabase(name) }
    }
}
