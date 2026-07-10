package com.saiyanstrong.di

import android.content.Context
import androidx.room.Room
import com.saiyanstrong.data.local.MIGRATION_3_4
import com.saiyanstrong.data.local.MIGRATION_4_5
import com.saiyanstrong.data.local.MIGRATION_5_6
import com.saiyanstrong.data.local.MIGRATION_6_7
import com.saiyanstrong.data.local.MIGRATION_7_8
import com.saiyanstrong.data.local.MIGRATION_8_9
import com.saiyanstrong.data.local.AppDatabase
import com.saiyanstrong.data.local.dao.BarPathMetricsDao
import com.saiyanstrong.data.local.dao.BodyWeightDao
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.dao.ExerciseLogDao
import com.saiyanstrong.data.local.dao.SessionDao
import com.saiyanstrong.data.local.dao.SetLogDao
import com.saiyanstrong.data.local.dao.TemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "saiyanstrong.db")
            .addMigrations(
                object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                    }
                },
                object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE set_logs ADD COLUMN is_failure INTEGER NOT NULL DEFAULT 0")
                    }
                },
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .build()

    @Provides
    @Singleton
    fun provideExerciseDao(database: AppDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    @Singleton
    fun provideExerciseLogDao(database: AppDatabase): ExerciseLogDao = database.exerciseLogDao()

    @Provides
    @Singleton
    fun provideSetLogDao(database: AppDatabase): SetLogDao = database.setLogDao()

    @Provides
    @Singleton
    fun provideTemplateDao(database: AppDatabase): TemplateDao = database.templateDao()

    @Provides
    @Singleton
    fun provideBodyWeightDao(database: AppDatabase): BodyWeightDao = database.bodyWeightDao()

    @Provides
    @Singleton
    fun provideBarPathMetricsDao(database: AppDatabase): BarPathMetricsDao = database.barPathMetricsDao()
}
