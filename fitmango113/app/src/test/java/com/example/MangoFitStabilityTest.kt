package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Content
import com.example.ai.GenerateContentRequest
import com.example.ai.GenerateContentResponse
import com.example.ai.Part
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangoFitStabilityTest {
    private fun recipe() = RecipeEntity(title = "Owsianka", description = "Porcje: 1",
        ingredients = "Płatki — 50 g", instructions = "Ugotuj płatki.",
        calories = 200, protein = 8, carbs = 30, fat = 4, category = "Śniadanie")
    private fun ingredient(name: String = "Płatki") = RecipeIngredientEntity(
        recipeId = 0, foodName = name, quantity = 50f, grams = 50f)

    @Test fun partialRecipePersistsAndCanBeEditedBeforeInstructionsAreAdded(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val partial = recipe().copy(instructions = "", source = "https://cookidoo.pl/recipes/r1")
            val id = dao.saveImportedRecipe(partial, listOf(ingredient()))
            assertEquals("", dao.recipeById(id)!!.instructions)
            assertEquals(200, dao.recipeById(id)!!.calories)
            assertEquals(id, dao.saveImportedRecipe(partial, listOf(ingredient())))
            dao.updateRecipeWithIngredients(partial.copy(id = id, title = "Nowa nazwa"), listOf(ingredient("Nowy składnik")))
            assertEquals("Nowa nazwa", dao.recipeById(id)!!.title)
            assertEquals("", dao.recipeById(id)!!.instructions)
            assertEquals("Nowy składnik", dao.getAllRecipeIngredients().first().single().foodName)
            dao.updateRecipeWithIngredients(dao.recipeById(id)!!.copy(instructions = "Ugotuj."), listOf(ingredient()))
            assertEquals("Ugotuj.", dao.recipeById(id)!!.instructions)
            assertTrue(runCatching { dao.saveImportedRecipe(partial.copy(title = ""), emptyList()) }.isFailure)
            assertTrue(runCatching { dao.updateRecipeWithIngredients(dao.recipeById(id)!!.copy(ingredients = ""), emptyList()) }.isFailure)
            assertEquals("Ugotuj.", dao.recipeById(id)!!.instructions)
        } finally { db.close() }
    }

    @Test fun repeatedImportKeepsOneCompleteRecipe(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dietaDao()
            val id = dao.saveImportedRecipe(recipe(), listOf(ingredient()))
            assertEquals(id, dao.saveImportedRecipe(recipe(), listOf(ingredient())))
            assertEquals(1, dao.getAllRecipes().first().size)
            assertEquals(id, dao.getAllRecipeIngredients().first().single().recipeId)
        } finally { db.close() }
    }

    @Test fun ingredientWriteFailureRollsBackWholeImport(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER reject_test_ingredient BEFORE INSERT ON recipe_ingredients
                WHEN NEW.foodName = 'fail' BEGIN SELECT RAISE(ABORT, 'test failure'); END
            """.trimIndent())
            val result = runCatching { db.dietaDao().saveImportedRecipe(recipe(), listOf(ingredient("fail"))) }
            assertTrue(result.isFailure)
            assertTrue(db.dietaDao().getAllRecipes().first().isEmpty())
            assertTrue(db.dietaDao().getAllRecipeIngredients().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun restartPreservesOfflineUserData(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "stability-restart-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            val dao = db.dietaDao()
            val id = dao.saveImportedRecipe(recipe(), listOf(ingredient()))
            dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Śniadanie",
                recipeId = id, recipeTitle = "Owsianka", servings = 2f, calories = 400))
            dao.saveUserProfile(UserProfileEntity(name = "MangoFit test", targetCalories = 1900))
            dao.replaceShoppingList(listOf(ShoppingItemEntity(itemName = "Płatki", category = "Zboża",
                amount = "100 g", isChecked = true)))
            db.close()
            db = open()
            assertEquals(id, db.dietaDao().getAllRecipes().first().single().id)
            assertEquals(2f, db.dietaDao().getAllMealPlans().first().single().servings, 0.001f)
            assertEquals(1900, db.dietaDao().getUserProfile().first()?.targetCalories)
            assertTrue(db.dietaDao().getAllShoppingItems().first().single().isChecked)
            assertEquals(1, db.dietaDao().getAllRecipeIngredients().first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun moshiGeneratedGeminiAdaptersRoundTrip() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val request = GenerateContentRequest(contents = listOf(Content(listOf(Part("Przepis")))))
        val adapter = moshi.adapter(GenerateContentRequest::class.java)
        assertEquals(request, adapter.fromJson(adapter.toJson(request)))
        val response = moshi.adapter(GenerateContentResponse::class.java).fromJson(
            """{"candidates":[{"content":{"parts":[{"text":"Odpowiedź"}]}}]}""")
        assertEquals("Odpowiedź", response?.candidates?.single()?.content?.parts?.single()?.text)
    }
}
