package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DietaRepository(private val dao: DietaDao) {
    suspend fun repairKnownPlanDates() = dao.repairKnownPlanDates()
    suspend fun assignLegacyPlanWeek(ids: List<Long>, week: String) = dao.assignLegacyPlanWeek(ids, week)
    suspend fun toggleRecipeFavorite(id: Long) = dao.toggleRecipeFavorite(id)
    val beverageLogs = dao.getBeverageLogs()
    val creatineLogs = dao.getCreatineLogs()
    suspend fun insertBeverageLog(log: BeverageLogEntity): Long {
        require(log.amountMl in 1..10000 && log.type.isNotBlank() && log.type.length <= 40)
        java.time.LocalDate.parse(log.date)
        return dao.insertBeverageLog(log)
    }
    suspend fun deleteBeverageLog(id: Long) = dao.deleteBeverageLog(id)
    suspend fun markCreatineTaken(date: String): Long {
        java.time.LocalDate.parse(date)
        return dao.insertCreatineLog(CreatineLogEntity(date = date))
    }
    suspend fun deleteCreatineLog(date: String) = dao.deleteCreatineLog(date)
    suspend fun updateRecipePhoto(id: Long, imageUrl: String, description: String) =
        dao.updateRecipePhoto(id, imageUrl, description)

    val allRecipes: Flow<List<RecipeEntity>> = dao.getAllRecipes()
    val allRecipeIngredients: Flow<List<RecipeIngredientEntity>> = dao.getAllRecipeIngredients()
    val allMealPlans: Flow<List<MealPlanEntity>> = dao.getAllMealPlans()
    val allShoppingItems: Flow<List<ShoppingItemEntity>> = dao.getAllShoppingItems()
    val allWeightLogs: Flow<List<WeightLogEntity>> = dao.getAllWeightLogs()
    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()
    val allBodyMeasurements: Flow<List<BodyMeasurementEntity>> = dao.getAllBodyMeasurements()
    suspend fun getAllBodyMeasurementsDirect(): List<BodyMeasurementEntity> = dao.getAllBodyMeasurementsDirect()
    val allBodyPhotos: Flow<List<BodyPhotoEntity>> = dao.getAllBodyPhotos()
    val healthConnectSync: Flow<HealthConnectSyncEntity?> = dao.getHealthConnectSync()

    suspend fun insertRecipe(recipe: RecipeEntity): Long = dao.insertRecipe(recipe)
    suspend fun saveImportedRecipe(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>): Long =
        dao.saveImportedRecipe(recipe, ingredients)
    suspend fun replaceShoppingWeek(week: String, items: List<ShoppingItemEntity>) = dao.replaceShoppingWeek(week, items)
    suspend fun replaceShoppingList(items: List<ShoppingItemEntity>) = dao.replaceShoppingList(items)
    suspend fun updateRecipeWithIngredients(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>) =
        dao.updateRecipeWithIngredients(recipe, ingredients)
    suspend fun insertMealPlans(meals: List<MealPlanEntity>) = dao.insertMealPlans(meals)
    suspend fun deleteRecipe(recipe: RecipeEntity) = dao.deleteRecipeAndPlans(recipe)
    suspend fun mergeDuplicateRecipe(duplicate: RecipeEntity, keeper: RecipeEntity) = dao.mergeDuplicateRecipe(duplicate, keeper)
    suspend fun updateRecipe(recipe: RecipeEntity) = dao.updateRecipe(recipe)

    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>> = dao.getIngredientsForRecipe(recipeId)
    suspend fun insertIngredient(ingredient: RecipeIngredientEntity) = dao.insertIngredient(ingredient)
    suspend fun updateIngredient(ingredient: RecipeIngredientEntity) = dao.updateIngredient(ingredient)
    suspend fun deleteIngredient(ingredient: RecipeIngredientEntity) = dao.deleteIngredient(ingredient)
    suspend fun deleteIngredientsForRecipe(recipeId: Long) = dao.deleteIngredientsForRecipe(recipeId)

    suspend fun addScannedProductMeal(recipe: RecipeEntity, ingredient: RecipeIngredientEntity, day: String, mealType: String): Long =
        dao.addScannedProductMeal(recipe, ingredient, day, mealType)

    suspend fun insertMealPlan(meal: MealPlanEntity): Long = dao.insertMealPlan(meal)
    suspend fun copyMeals(ids: List<Long>, day: String, type: String? = null) = dao.copyMeals(ids, day, type)
    suspend fun insertSavedMeals(entries: List<com.example.viewmodel.SavedMealEntry>, day: String? = null) = dao.insertSavedMeals(entries, day)
    suspend fun updateMealPlan(meal: MealPlanEntity) = dao.updateMealPlan(meal)
    suspend fun deleteMealPlan(meal: MealPlanEntity) = dao.deleteMealPlan(meal)
    suspend fun clearMealPlans() = dao.clearMealPlans()

    suspend fun insertShoppingItem(item: ShoppingItemEntity) = dao.insertShoppingItem(item)
    suspend fun updateShoppingItem(item: ShoppingItemEntity) = dao.updateShoppingItem(item)
    suspend fun deleteShoppingItem(item: ShoppingItemEntity) = dao.deleteShoppingItem(item)
    suspend fun clearShoppingList() = dao.clearShoppingList()

    suspend fun insertWeightLog(log: WeightLogEntity) = dao.insertWeightLog(log)
    suspend fun saveUserProfile(profile: UserProfileEntity) = dao.saveUserProfile(profile)
    suspend fun insertBodyMeasurement(measurement: BodyMeasurementEntity) = dao.insertBodyMeasurement(measurement)
    suspend fun insertBodyPhoto(photo: BodyPhotoEntity) = dao.insertBodyPhoto(photo)

    suspend fun insertDocument(document: DocumentEntity): Long = dao.insertDocument(document)
    suspend fun deleteDocument(document: DocumentEntity) = dao.deleteDocument(document)
    suspend fun updateDocument(document: DocumentEntity) = dao.updateDocument(document)
    
    suspend fun getGeminiResponse(promptHash: Int): String? = dao.getCachedResponse(promptHash)
    suspend fun insertGeminiCache(cache: GeminiCacheEntity) = dao.insertCache(cache)

    suspend fun saveHealthConnectSync(sync: HealthConnectSyncEntity) = dao.saveHealthConnectSync(sync)
    suspend fun findMeasurementByHealthRecordId(recordId: String) = dao.findMeasurementByHealthRecordId(recordId)
    suspend fun findMeasurementByDate(date: String) = dao.findMeasurementByDate(date)

    suspend fun seedInitialData() {
        val existingRecipes = dao.getAllRecipes().first()
        val existingMealPlans = dao.getAllMealPlans().first()
        val existingProfile = dao.getUserProfile().first()
        if (existingRecipes.isNotEmpty() || existingMealPlans.isNotEmpty() || existingProfile != null) {
            return
        }

        dao.saveUserProfile(UserProfileEntity(id = 1, name = "Ania", targetCalories = 2000, targetProtein = 130, targetCarbs = 200, targetFat = 65))
        
        val r1 = dao.insertRecipe(RecipeEntity(
            title = "Avocado & Egg Toast",
            description = "High protein breakfast toast with creamy avocado and poached eggs.",
            ingredients = "2 slices whole grain bread, 1 ripe avocado, 2 eggs, lemon juice, salt, pepper",
            instructions = "Toast bread. Mash avocado with lemon juice and salt. Top toast with mashed avocado and fried or poached eggs.",
            calories = 380, protein = 18, carbs = 32, fat = 20,
            category = "Breakfast", source = "App"
        ))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r1, foodName = "Whole grain bread", quantity = 2f, unit = "slices", grams = 80f, caloriesPer100g = 250f, proteinPer100g = 9f, carbsPer100g = 45f, fatPer100g = 3f))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r1, foodName = "Avocado", quantity = 1f, unit = "szt.", grams = 100f, caloriesPer100g = 160f, proteinPer100g = 2f, carbsPer100g = 9f, fatPer100g = 15f))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r1, foodName = "Eggs", quantity = 2f, unit = "szt.", grams = 100f, caloriesPer100g = 155f, proteinPer100g = 13f, carbsPer100g = 1f, fatPer100g = 11f))

        val r2 = dao.insertRecipe(RecipeEntity(
            title = "Grilled Chicken Salad",
            description = "Fresh garden greens with tender grilled chicken breast and olive oil dressing.",
            ingredients = "200g chicken breast, mixed greens, cherry tomatoes, cucumber, olive oil, balsamic vinegar",
            instructions = "Grill chicken breast until cooked. Chop vegetables and toss with greens. Slice chicken and place on top with olive oil dressing.",
            calories = 450, protein = 42, carbs = 12, fat = 24,
            category = "Lunch", source = "App"
        ))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r2, foodName = "Chicken breast", quantity = 200f, unit = "g", grams = 200f, caloriesPer100g = 165f, proteinPer100g = 31f, carbsPer100g = 0f, fatPer100g = 3f))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r2, foodName = "Mixed greens", quantity = 100f, unit = "g", grams = 100f, caloriesPer100g = 25f, proteinPer100g = 2f, carbsPer100g = 4f, fatPer100g = 0f))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r2, foodName = "Olive oil", quantity = 15f, unit = "ml", grams = 15f, caloriesPer100g = 884f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 100f))

        val r3 = dao.insertRecipe(RecipeEntity(
            title = "Baked Salmon with Quinoa",
            description = "Omega-3 rich salmon fillet served with fluffy quinoa and steamed asparagus.",
            ingredients = "200g salmon fillet, 1 cup cooked quinoa, 1 bunch asparagus, olive oil, garlic, lemon",
            instructions = "Bake salmon at 200°C for 15 mins. Cook quinoa. Steam asparagus and serve together with a squeeze of lemon.",
            calories = 580, protein = 48, carbs = 40, fat = 22,
            category = "Dinner", source = "App"
        ))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r3, foodName = "Salmon fillet", quantity = 200f, unit = "g", grams = 200f, caloriesPer100g = 208f, proteinPer100g = 20f, carbsPer100g = 0f, fatPer100g = 13f))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r3, foodName = "Quinoa", quantity = 150f, unit = "g", grams = 150f, caloriesPer100g = 120f, proteinPer100g = 4f, carbsPer100g = 21f, fatPer100g = 2f))
        dao.insertIngredient(RecipeIngredientEntity(recipeId = r3, foodName = "Asparagus", quantity = 100f, unit = "g", grams = 100f, caloriesPer100g = 20f, proteinPer100g = 2f, carbsPer100g = 4f, fatPer100g = 0.2f))

        dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Śniadanie", recipeId = r1, recipeTitle = "Avocado & Egg Toast", servings = 1f, calories = 380))
        dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Obiad", recipeId = r2, recipeTitle = "Grilled Chicken Salad", servings = 1f, calories = 450))
        dao.insertMealPlan(MealPlanEntity(dayOfWeek = "Poniedziałek", mealType = "Kolacja", recipeId = r3, recipeTitle = "Baked Salmon with Quinoa", servings = 1f, calories = 580))
    }
}
