package com.example.voicerecorderlocation

import android.app.Application
import com.example.voicerecorderlocation.di.ServiceLocator

class VoiceRecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

