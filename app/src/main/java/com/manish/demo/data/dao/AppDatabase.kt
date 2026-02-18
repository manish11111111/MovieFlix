package com.manish.demo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.manish.demo.data.dao.UserDao
import com.manish.demo.entities.GenreEntity
import com.manish.demo.entities.LanguageEntity
import com.manish.demo.entities.MovieEntity
import com.manish.demo.entities.MovieGenreCrossRef
import com.manish.demo.entities.MovieLanguageCrossRef
import com.manish.demo.entities.SubscriptionPlanEntity
import com.manish.demo.entities.UserEntity
import com.manish.demo.entities.UserProfileEntity
import com.manish.demo.entities.UserSubscriptionEntity

@Database(
    entities = [
        UserEntity::class,
        UserProfileEntity::class,
        SubscriptionPlanEntity::class,
        UserSubscriptionEntity::class,

        MovieEntity::class,
        GenreEntity::class,
        LanguageEntity::class,

        MovieGenreCrossRef::class,
        MovieLanguageCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "movieflix_relational_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
