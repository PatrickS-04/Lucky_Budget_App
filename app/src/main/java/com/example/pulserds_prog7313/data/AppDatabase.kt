package com.example.pulserds_prog7313.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Room Persistence Library used for local database management. We used the official android documents for secure implementation
// [1] Google, "Save data in a local database using Room," Android Developers, 2024.
// [6] Google, AndroidX Room, ver. 2.7.0-alpha11. [Software]. Android Developers, 2024.
@Database(
    entities = [
        User::class,
        Category::class,
        ExpenseEntry::class,
        BankAccount::class,
        SavingsGoal::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseEntryDao(): ExpenseEntryDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pulser_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}