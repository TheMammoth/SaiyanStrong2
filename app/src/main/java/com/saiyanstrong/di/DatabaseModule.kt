package com.saiyanstrong.di

import android.content.Context
import androidx.room.Room
import com.saiyanstrong.data.local.AppDatabase
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.dao.ExerciseLogDao
import com.saiyanstrong.data.local.dao.SessionDao
import com.saiyanstrong.data.local.dao.SetLogDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, "saiyanstrong.db").build()

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
}
