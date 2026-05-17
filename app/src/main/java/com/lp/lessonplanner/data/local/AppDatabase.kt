package com.lp.lessonplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SubjectEntity::class, CurriculumEntity::class, SavedPlanEntity::class, SettingEntity::class, CreditRedemptionEntity::class, UserEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun curriculumDao(): CurriculumDao
    abstract fun savedPlanDao(): SavedPlanDao
    abstract fun settingsDao(): SettingsDao
    abstract fun creditRedemptionDao(): CreditRedemptionDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lesson_plans_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
