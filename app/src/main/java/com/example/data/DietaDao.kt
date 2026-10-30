package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

@Dao
interface DietaDao {
    @Transaction
    suspend fun saveChatRecipeAndPlan(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>, existingId: Long?,
        date: String?, slot: String, portions: Float): Long {
        require(portions.isFinite() && portions in .25f..100f)
        date?.let { java.time.LocalDate.parse(it) }
        val id = existingId?.takeIf { recipeById(it) != null } ?: saveImportedRecipe(recipe, ingredients)
        if (date != null) insertMealPlan(MealPlanEntity(dayOfWeek = date, mealType = slot, recipeId = id,
            recipeTitle = recipe.title, servings = portions, calories = (recipe.calories * portions).roundToInt()))
        return id
    }
    @Query("SELECT * FROM meal_plans WHERE id = :id LIMIT 1")
    suspend fun mealById(id: Long): MealPlanEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun ingredientsByRecipeId(recipeId: Long): List<RecipeIngredientEntity>

    @Transaction
    suspend fun copyMeals(ids: List<Long>, day: String, type: String?): List<Long> {
        require(ids.isNotEmpty())
        return ids.distinct().map { id ->
            val original = mealById(id) ?: error("Posiłek został usunięty.")
            val copiedId = insertMealPlan(com.example.viewmodel.QuickUseSupport.freshCopy(original, day, type ?: original.mealType))
            ingredientsByRecipeId(-id).forEach { insertIngredient(it.copy(id = 0, recipeId = -copiedId)) }
            copiedId
        }
    }
    @Transaction
    suspend fun insertSavedMeals(entries: List<com.example.viewmodel.SavedMealEntry>, day: String?): List<Long> {
        require(entries.isNotEmpty())
        return entries.map { entry ->
        val meal = entry.meal
        require(recipeById(meal.recipeId) != null) { "Przepis z zestawu został usunięty." }
        val id = insertMealPlan(com.example.viewmodel.QuickUseSupport.freshCopy(meal, day ?: meal.dayOfWeek, meal.mealType))
        entry.overrides.forEach { insertIngredient(it.copy(id = 0, recipeId = -id)) }
        id
        }
    }

    @Query("UPDATE recipes SET isFavorite = 1 WHERE id = :id")
    suspend fun markRecipeFavorite(id: Long)

    @Query("UPDATE recipes SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleRecipeFavorite(id: Long)

    @Query("SELECT * FROM beverage_logs ORDER BY timestamp DESC, id DESC")
    fun getBeverageLogs(): Flow<List<BeverageLogEntity>>

    @Insert
    suspend fun insertBeverageLog(log: BeverageLogEntity): Long

    @Query("DELETE FROM beverage_logs WHERE id = :id")
    suspend fun deleteBeverageLog(id: Long)

    @Query("SELECT * FROM creatine_logs ORDER BY date DESC")
    fun getCreatineLogs(): Flow<List<CreatineLogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCreatineLog(log: CreatineLogEntity): Long

    @Query("DELETE FROM creatine_logs WHERE date = :date")
    suspend fun deleteCreatineLog(date: String)

    @Query("UPDATE recipes SET imageUrl = :imageUrl, description = :description WHERE id = :id")
    suspend fun updateRecipePhoto(id: Long, imageUrl: String, description: String)

    // Compare-and-set prevents a background download from overwriting a user's edit.
    @Query("UPDATE recipes SET imageUrl = :image, description = :description WHERE id = :id AND imageUrl = :oldImage AND title = :title AND ingredients = :ingredients AND description = :oldDescription")
    suspend fun saveAutomaticPhoto(id: Long, oldImage: String, title: String, ingredients: String, oldDescription: String, image: String, description: String): Int

    @Query("SELECT id FROM recipes WHERE title = :title AND ingredients = :ingredients AND instructions = :instructions LIMIT 1")
    suspend fun findImportedRecipe(title: String, ingredients: String, instructions: String): Long?

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun recipeById(id: Long): RecipeEntity?

    // Public sources can omit preparation; an empty instruction field is valid stored data.
    @Transaction
    suspend fun saveImportedRecipe(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>): Long {
        require(recipe.title.isNotBlank() && recipe.ingredients.isNotBlank()) {
            "Przepis musi zawierać nazwę i składniki."
        }
        findImportedRecipe(recipe.title, recipe.ingredients, recipe.instructions)?.let { id ->
            val old = recipeById(id)
            if (old != null && recipe.calories > 0) {
                val updated = old.copy(
                    calories = old.calories.takeIf { it > 0 } ?: recipe.calories,
                    protein = old.protein.takeIf { it > 0 } ?: recipe.protein,
                    carbs = old.carbs.takeIf { it > 0 } ?: recipe.carbs,
                    fat = old.fat.takeIf { it > 0 } ?: recipe.fat)
                if (updated != old) updateRecipe(updated.copy(description = old.description + "\nUzupełniono brakujące wartości odżywcze przy ponownym imporcie (wartości szacunkowe)."))
            }
            return id
        }
        val id = insertRecipe(recipe)
        ingredients.forEach { insertIngredient(it.copy(id = 0, recipeId = id)) }
        return id
    }

    @Transaction
    suspend fun addScannedProductMeal(recipe: RecipeEntity, ingredient: RecipeIngredientEntity, day: String, mealType: String): Long {
        val recipeId = saveImportedRecipe(recipe, listOf(ingredient))
        return insertMealPlan(MealPlanEntity(dayOfWeek = day, mealType = mealType, recipeId = recipeId,
            recipeTitle = recipe.title, servings = 1f, calories = recipe.calories))
    }

    @Transaction
    suspend fun replaceShoppingList(items: List<ShoppingItemEntity>) {
        clearShoppingList()
        items.forEach { insertShoppingItem(it) }
    }

    @Query("DELETE FROM shopping_items WHERE weekStart = :week AND isManual = 0")
    suspend fun clearGeneratedShoppingWeek(week: String)

    @Transaction
    suspend fun replaceShoppingWeek(week: String, items: List<ShoppingItemEntity>) {
        require(items.all { it.weekStart == week && !it.isManual })
        clearGeneratedShoppingWeek(week)
        items.forEach { insertShoppingItem(it) }
    }

    @Transaction
    suspend fun updateRecipeWithIngredients(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>) {
        require(recipe.title.isNotBlank() && recipe.ingredients.isNotBlank())
        updateRecipe(recipe)
        deleteIngredientsForRecipe(recipe.id)
        ingredients.forEach { insertIngredient(it.copy(id = 0, recipeId = recipe.id)) }
    }

    @Transaction
    suspend fun insertMealPlans(meals: List<MealPlanEntity>) {
        meals.forEach { insertMealPlan(it) }
    }
    @Query("SELECT * FROM meal_plans WHERE recipeId = :id")
    suspend fun plannedUses(id: Long): List<MealPlanEntity>

    @Transaction
    suspend fun deleteRecipeAndPlans(recipe: RecipeEntity) {
        for (meal in plannedUses(recipe.id)) {
            deleteIngredientsForRecipe(-meal.id)
            deleteMealPlan(meal)
        }
        deleteIngredientsForRecipe(recipe.id)
        deleteRecipe(recipe)
    }

    @Transaction
    suspend fun mergeDuplicateRecipe(duplicate: RecipeEntity, keeper: RecipeEntity) {
        require(duplicate.id != keeper.id && duplicate.ingredients.trim() == keeper.ingredients.trim()
            && duplicate.instructions.trim() == keeper.instructions.trim()
            && duplicate.calories == keeper.calories && duplicate.protein == keeper.protein
            && duplicate.carbs == keeper.carbs && duplicate.fat == keeper.fat)
        plannedUses(duplicate.id).forEach { updateMealPlan(it.copy(recipeId = keeper.id, recipeTitle = keeper.title)) }
        if (duplicate.isFavorite) markRecipeFavorite(keeper.id)
        if (keeper.imageUrl.isBlank() && duplicate.imageUrl.isNotBlank()) updateRecipePhoto(keeper.id, duplicate.imageUrl, keeper.description + duplicate.description.substringAfter("\n\nZdjęcie: ", "").let { if (it.isBlank()) "" else "\n\nZdjęcie: $it" })
        deleteIngredientsForRecipe(duplicate.id)
        deleteRecipe(duplicate)
    }

    // Recipes
    @Query("SELECT * FROM recipes")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    // Recipe Ingredients
    @Query("SELECT * FROM recipe_ingredients")
    fun getAllRecipeIngredients(): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long

    @Update
    suspend fun updateIngredient(ingredient: RecipeIngredientEntity)

    @Delete
    suspend fun deleteIngredient(ingredient: RecipeIngredientEntity)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Long)

    @Query("SELECT * FROM meal_plans WHERE id = :id LIMIT 1")
    suspend fun mealPlanById(id: Long): MealPlanEntity?

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun ingredientsForRecipeDirect(recipeId: Long): List<RecipeIngredientEntity>

    // Meal Plans
    @Query("SELECT * FROM meal_plans")
    fun getAllMealPlans(): Flow<List<MealPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlanRecord(meal: MealPlanEntity): Long

    suspend fun insertMealPlan(meal: MealPlanEntity): Long =
        insertMealPlanRecord(com.example.viewmodel.LegacyPlanDates.newEntry(meal))

    @Query("SELECT * FROM meal_plans")
    suspend fun mealPlansDirect(): List<MealPlanEntity>

    @Transaction
    suspend fun repairKnownPlanDates() {
        mealPlansDirect().filter { com.example.viewmodel.MealPlannerSupport.parsePlanDate(it.dayOfWeek) == null &&
            com.example.viewmodel.LegacyPlanDates.knownDate(it) != null }.forEach { meal ->
            updateMealPlan(com.example.viewmodel.LegacyPlanDates.assign(meal, java.time.LocalDate.now()))
        }
    }

    @Transaction
    suspend fun assignLegacyPlanWeek(ids: List<Long>, week: String) {
        val reference = java.time.LocalDate.parse(week)
        ids.distinct().forEach { id ->
            val meal = mealById(id) ?: error("Posiłek został usunięty")
            if (com.example.viewmodel.LegacyPlanDates.needsWeek(meal))
                updateMealPlan(com.example.viewmodel.LegacyPlanDates.assign(meal, reference))
        }
    }

    @Update
    suspend fun updateMealPlan(meal: MealPlanEntity)

    @Delete
    suspend fun deleteMealPlan(meal: MealPlanEntity)

    @Query("DELETE FROM meal_plans")
    suspend fun clearMealPlans()

    // Shopping List
    @Query("SELECT * FROM shopping_items")
    fun getAllShoppingItems(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ShoppingItemEntity)

    @Update
    suspend fun updateShoppingItem(item: ShoppingItemEntity)

    @Delete
    suspend fun deleteShoppingItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items")
    suspend fun clearShoppingList()

    // Weight Logs
    @Query("SELECT * FROM weight_logs ORDER BY id DESC")
    fun getAllWeightLogs(): Flow<List<WeightLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLogEntity)

    @Delete
    suspend fun deleteWeightLog(log: WeightLogEntity)

    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)

    // Body Measurements
    @Query("SELECT * FROM body_measurements ORDER BY id DESC")
    fun getAllBodyMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY id DESC")
    suspend fun getAllBodyMeasurementsDirect(): List<BodyMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMeasurement(measurement: BodyMeasurementEntity)

    @Delete
    suspend fun deleteBodyMeasurement(measurement: BodyMeasurementEntity)

    // Body Photos
    @Query("SELECT * FROM body_photos ORDER BY id DESC")
    fun getAllBodyPhotos(): Flow<List<BodyPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyPhoto(photo: BodyPhotoEntity)

    @Query("SELECT * FROM body_photos WHERE id = :id LIMIT 1")
    suspend fun bodyPhotoById(id: Long): BodyPhotoEntity?

    @Query("SELECT * FROM body_photos ORDER BY id DESC")
    suspend fun getBodyPhotosDirect(): List<BodyPhotoEntity>

    @Update
    suspend fun updateBodyPhoto(photo: BodyPhotoEntity): Int

    @Delete
    suspend fun deleteBodyPhoto(photo: BodyPhotoEntity)

    // Documents
    @Query("SELECT * FROM documents ORDER BY id DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    // Gemini Cache
    @Query("SELECT response FROM gemini_cache WHERE promptHash = :promptHash")
    suspend fun getCachedResponse(promptHash: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: GeminiCacheEntity)

    // Health Connect Sync
    @Query("SELECT * FROM health_connect_sync WHERE id = 1")
    fun getHealthConnectSync(): Flow<HealthConnectSyncEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHealthConnectSync(sync: HealthConnectSyncEntity)

    @Query("SELECT * FROM body_measurements WHERE healthRecordId = :recordId LIMIT 1")
    suspend fun findMeasurementByHealthRecordId(recordId: String): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements WHERE date = :date LIMIT 1")
    suspend fun findMeasurementByDate(date: String): BodyMeasurementEntity?
}
