package com.merowall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MeroWallApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Timber logging (debug only)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Notification channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channels = listOf(
                NotificationChannel(
                    "merowall_default",
                    "General",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "General notifications" },
                NotificationChannel(
                    "merowall_chat",
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Chat messages" },
                NotificationChannel(
                    "merowall_social",
                    "Social",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Likes, comments, follows" }
            )

            channels.forEach { manager.createNotificationChannel(it) }
        }
    }
}
