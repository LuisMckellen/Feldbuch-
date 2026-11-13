package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      TreeFileEntity::class,
      ChatMessageEntity::class,
      SessionNoteEntity::class,
      OutboxEntity::class,
    ],
  version = 5,
  exportSchema = false,
)
abstract class FeldbuchDatabase : RoomDatabase() {
  abstract fun feldbuchDao(): FeldbuchDao

  companion object {
    @Volatile private var INSTANCE: FeldbuchDatabase? = null

    fun getInstance(context: Context): FeldbuchDatabase =
      INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                FeldbuchDatabase::class.java,
                "feldbuch.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
  }
}
