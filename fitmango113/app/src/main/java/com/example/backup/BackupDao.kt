package com.example.backup

import androidx.room.*
import com.example.data.*

// All user tables are read together and restored together; schema 9; old schema 8 backups use default unassigned shopping weeks.
data class BackupData(
    val recipes: List<RecipeEntity>,
    val ingredients: List<RecipeIngredientEntity>,
    val meals: List<MealPlanEntity>,
    val shopping: List<ShoppingItemEntity>,
    val weights: List<WeightLogEntity>,
    val profiles: List<UserProfileEntity>,
    val measurements: List<BodyMeasurementEntity>,
    val health: List<HealthConnectSyncEntity>,
    val photos: List<BodyPhotoEntity>,
    val documents: List<DocumentEntity>,
    val beverages: List<BeverageLogEntity>,
    val creatine: List<CreatineLogEntity>
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM recipes")
    suspend fun read_recipes(): List<RecipeEntity>

    @Query("DELETE FROM recipes")
    suspend fun clear_recipes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_recipes(items: List<RecipeEntity>)

    @Query("SELECT * FROM recipe_ingredients")
    suspend fun read_ingredients(): List<RecipeIngredientEntity>

    @Query("DELETE FROM recipe_ingredients")
    suspend fun clear_ingredients()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_ingredients(items: List<RecipeIngredientEntity>)

    @Query("SELECT * FROM meal_plans")
    suspend fun read_meals(): List<MealPlanEntity>

    @Query("DELETE FROM meal_plans")
    suspend fun clear_meals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_meals(items: List<MealPlanEntity>)

    @Query("SELECT * FROM shopping_items")
    suspend fun read_shopping(): List<ShoppingItemEntity>

    @Query("DELETE FROM shopping_items")
    suspend fun clear_shopping()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_shopping(items: List<ShoppingItemEntity>)

    @Query("SELECT * FROM weight_logs")
    suspend fun read_weights(): List<WeightLogEntity>

    @Query("DELETE FROM weight_logs")
    suspend fun clear_weights()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_weights(items: List<WeightLogEntity>)

    @Query("SELECT * FROM user_profile")
    suspend fun read_profiles(): List<UserProfileEntity>

    @Query("DELETE FROM user_profile")
    suspend fun clear_profiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_profiles(items: List<UserProfileEntity>)

    @Query("SELECT * FROM body_measurements")
    suspend fun read_measurements(): List<BodyMeasurementEntity>

    @Query("DELETE FROM body_measurements")
    suspend fun clear_measurements()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_measurements(items: List<BodyMeasurementEntity>)

    @Query("SELECT * FROM health_connect_sync")
    suspend fun read_health(): List<HealthConnectSyncEntity>

    @Query("DELETE FROM health_connect_sync")
    suspend fun clear_health()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_health(items: List<HealthConnectSyncEntity>)

    @Query("SELECT * FROM body_photos")
    suspend fun read_photos(): List<BodyPhotoEntity>

    @Query("DELETE FROM body_photos")
    suspend fun clear_photos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_photos(items: List<BodyPhotoEntity>)

    @Query("SELECT * FROM documents")
    suspend fun read_documents(): List<DocumentEntity>

    @Query("DELETE FROM documents")
    suspend fun clear_documents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_documents(items: List<DocumentEntity>)

    @Query("SELECT * FROM beverage_logs")
    suspend fun read_beverages(): List<BeverageLogEntity>

    @Query("DELETE FROM beverage_logs")
    suspend fun clear_beverages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_beverages(items: List<BeverageLogEntity>)

    @Query("SELECT * FROM creatine_logs")
    suspend fun read_creatine(): List<CreatineLogEntity>

    @Query("DELETE FROM creatine_logs")
    suspend fun clear_creatine()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun write_creatine(items: List<CreatineLogEntity>)

    @Query("DELETE FROM gemini_cache")
    suspend fun clearCache()

    @Transaction
    suspend fun snapshot(): BackupData = BackupData(
        recipes = read_recipes(),
        ingredients = read_ingredients(),
        meals = read_meals(),
        shopping = read_shopping(),
        weights = read_weights(),
        profiles = read_profiles(),
        measurements = read_measurements(),
        health = read_health(),
        photos = read_photos(),
        documents = read_documents(),
        beverages = read_beverages(),
        creatine = read_creatine()
    )

    @Transaction
    suspend fun restore(data: BackupData) {
        clear_recipes()
        clear_ingredients()
        clear_meals()
        clear_shopping()
        clear_weights()
        clear_profiles()
        clear_measurements()
        clear_health()
        clear_photos()
        clear_documents()
        clear_beverages()
        clear_creatine()
        clearCache()
        write_recipes(data.recipes)
        write_ingredients(data.ingredients)
        write_meals(data.meals)
        write_shopping(data.shopping)
        write_weights(data.weights)
        write_profiles(data.profiles)
        write_measurements(data.measurements)
        write_health(data.health)
        write_photos(data.photos)
        write_documents(data.documents)
        write_beverages(data.beverages)
        write_creatine(data.creatine)
    }
}
