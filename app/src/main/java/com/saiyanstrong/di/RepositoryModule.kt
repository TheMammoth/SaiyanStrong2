package com.saiyanstrong.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Phase 2 will add @Binds for ExerciseRepository, SessionRepository, UserRepository.
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
