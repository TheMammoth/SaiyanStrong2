package com.saiyanstrong.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.dao.ExerciseLogDao
import com.saiyanstrong.data.local.dao.SessionDao
import com.saiyanstrong.data.local.dao.SetLogDao
import com.saiyanstrong.data.local.entity.ExerciseEntity
import com.saiyanstrong.data.local.entity.ExerciseLogEntity
import com.saiyanstrong.data.local.entity.SessionEntity
import com.saiyanstrong.data.local.entity.SetLogEntity

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Clear exercises so ExerciseSeeder re-inserts updated names on next app launch
        database.execSQL("DELETE FROM exercises")
    }
}

@Database(
    entities = [
        ExerciseEntity::class,
        SessionEntity::class,
        ExerciseLogEntity::class,
        SetLogEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun setLogDao(): SetLogDao
}
