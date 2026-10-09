package com.example

import android.app.Application
import android.util.Log
import com.example.data.SettingsPreferences
import com.example.data.db.AppDatabase

class VoiceBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set global uncaught exception handler to prevent hard crashes in background threads
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("VoiceBridgeApplication", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Safely pre-load native libraries
        try {
            System.loadLibrary("onnxruntime")
        } catch (t: Throwable) {
            Log.w("VoiceBridgeApplication", "onnxruntime pre-load: ${t.message}")
        }
        try {
            System.loadLibrary("sherpa-onnx-c-api")
        } catch (t: Throwable) {
            Log.w("VoiceBridgeApplication", "sherpa-onnx-c-api pre-load: ${t.message}")
        }
        try {
            System.loadLibrary("sherpa-onnx-jni")
        } catch (t: Throwable) {
            Log.w("VoiceBridgeApplication", "sherpa-onnx-jni pre-load: ${t.message}")
        }

        // Pre-initialize database and preferences
        AppDatabase.getInstance(this)
        SettingsPreferences.getInstance(this)
    }
}
