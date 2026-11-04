package com.meghanath.musix

import android.app.Application
import com.meghanath.musix.download.DownloaderEngine

class MusixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only remembers the app. The downloader itself starts the first time it is needed.
        DownloaderEngine.attach(this)
    }
}