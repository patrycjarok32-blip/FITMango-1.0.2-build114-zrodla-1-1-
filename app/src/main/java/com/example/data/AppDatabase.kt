package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        MealPlanEntity::class,
        ShoppingItemEntity::class,
        WeightLogEntity::class,
        UserProfileEntity::class,
        DocumentEntity::class,
        GeminiCacheEntity::class,
        BodyMeasurementEntity::class,
        BodyPhotoEntity::class,
        HealthConnectSyncEntity::class,
        BeverageLogEntity::class,
        CreatineLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dietaDao(): DietaDao
    abstract fun backupDao(): com.example.backup.BackupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE body_measurements ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE body_measurements ADD COLUMN healthRecordId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE body_measurements ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_connect_sync (
                        id INTEGER PRIMARY KEY NOT NULL,
                        isConnected INTEGER NOT NULL,
                        lastSyncTimeMillis INTEGER NOT NULL,
                        stepsToday INTEGER NOT NULL,
                        totalCaloriesBurned REAL NOT NULL,
                        activeCaloriesBurned REAL NOT NULL,
                        latestWeightKg REAL NOT NULL,
                        sleepDurationMinutes INTEGER NOT NULL,
                        syncStatusMessage TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS beverage_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL, amountMl INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS creatine_logs (date TEXT PRIMARY KEY NOT NULL, amountMg INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_plans ADD COLUMN completedDate TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Old global lists have no trustworthy week; preserve them separately.
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN weekStart TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moja_dieta_ai.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
