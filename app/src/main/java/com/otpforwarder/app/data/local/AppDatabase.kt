package com.otpforwarder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromForwardStatus(status: ForwardStatus): String = status.name

    @TypeConverter
    fun toForwardStatus(value: String): ForwardStatus = try {
        ForwardStatus.valueOf(value)
    } catch (e: IllegalArgumentException) {
        ForwardStatus.FAILED
    }
}

@Database(
    entities = [LogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "otp_forwarder.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
