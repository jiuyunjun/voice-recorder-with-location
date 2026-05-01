package com.example.voicerecorderlocation.di

import android.content.Context
import com.example.voicerecorderlocation.data.AppDatabase
import com.example.voicerecorderlocation.data.RecordingRepository

object ServiceLocator {
    lateinit var repository: RecordingRepository
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        val database = AppDatabase.create(appContext)
        repository = RecordingRepository(database.recordingDao(), database.locationPointDao())
    }
}

