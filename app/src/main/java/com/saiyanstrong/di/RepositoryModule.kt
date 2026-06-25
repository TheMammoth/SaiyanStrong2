package com.saiyanstrong.di

import com.saiyanstrong.data.repository.ExerciseRepositoryImpl
import com.saiyanstrong.data.repository.SessionRepositoryImpl
import com.saiyanstrong.data.repository.UserRepositoryImpl
import com.saiyanstrong.domain.repository.ExerciseRepository
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
