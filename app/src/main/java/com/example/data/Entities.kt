package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val ingredients: String, // comma separated or newline separated
    val instructions: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val category: String, // Breakfast, Lunch, Dinner, Snack
    val isFavorite: Boolean = false,
    val source: String = "App", // App, URL, Instagram, PDF, Cookidoo
    val imageUrl: String = ""
)

@Entity(tableName = "recipe_ingredients")
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val foodName: String,
    val quantity: Float = 100f,
    val unit: String = "g",
    val grams: Float = 100f,
    val caloriesPer100g: Float = 100f,
    val proteinPer100g: Float = 5f,
    val carbsPer100g: Float = 15f,
    val fatPer100g: Float = 3f
)

@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: String, // ISO date for new plans; legacy weekday names remain supported
    val mealType: String, // Śniadanie, II śniadanie, Obiad, Przekąska, Kolacja
    val recipeId: Long = 0,
    val recipeTitle: String,
    val servings: Float = 1.0f,
    val calories: Int,
    val isCompleted: Boolean = false,
    val completedDate: String? = null
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemName: String,
    val category: String, // Produce, Dairy, Meat, Pantry
    val isChecked: Boolean = false,
    val amount: String = "1 unit",
    @androidx.room.ColumnInfo(defaultValue = "''") val weekStart: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val isManual: Boolean = false
)

@Entity(tableName = "weight_logs")
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Float
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "User",
    val age: Int = 30,
    val heightCm: Float = 175f,
    val currentWeightKg: Float = 75f,
    val gender: String = "Mężczyzna",
    val activityLevel: String = "Umiarkowana aktywność",
    val goal: String = "Redukcja",
    val targetCalories: Int = 2200,
    val targetProtein: Int = 150,
    val targetCarbs: Int = 200,
    val targetFat: Int = 70
)

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Float,
    val waistCm: Float = 0f,
    val hipsCm: Float = 0f,
    val chestCm: Float = 0f,
    val armsCm: Float = 0f,
    val thighsCm: Float = 0f,
    val source: String = "MANUAL", // "MANUAL" or "HEALTH_CONNECT"
    val healthRecordId: String? = null,
    val timestamp: Long = 0L
)

@Entity(tableName = "health_connect_sync")
data class HealthConnectSyncEntity(
    @PrimaryKey val id: Int = 1,
    val isConnected: Boolean = false,
    val lastSyncTimeMillis: Long = 0L,
    val stepsToday: Long = 0L,
    val totalCaloriesBurned: Double = 0.0,
    val activeCaloriesBurned: Double = 0.0,
    val latestWeightKg: Double = 0.0,
    val sleepDurationMinutes: Long = 0L,
    val syncStatusMessage: String = ""
)

@Entity(tableName = "body_photos")
data class BodyPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val frontUri: String,
    val sideUri: String,
    val backUri: String
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uriString: String = "",
    val extractedText: String,
    val dateAdded: String,
    val aiAnalysisResult: String = ""
)

@Entity(tableName = "gemini_cache")
data class GeminiCacheEntity(
    @PrimaryKey val promptHash: Int,
    val prompt: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "beverage_logs")
data class BeverageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "creatine_logs")
data class CreatineLogEntity(
    @PrimaryKey val date: String,
    val amountMg: Int = 5000,
    val timestamp: Long = System.currentTimeMillis()
)
