package com.merowall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.merowall.utils.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MeroWallApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR
            )
            .build()

    override fun onCreate() {
        super.onCreate()

        // Firebase
        FirebaseApp.initializeApp(this)

        // Crashlytics - disable in debug
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }

        createNotificationChannels()
        SyncWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel("merowall_default", "General", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "General notifications" },
                NotificationChannel("merowall_chat", "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Chat messages"; enableVibration(true) },
                NotificationChannel("merowall_social", "Social", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Likes, comments, follows" }
            ).forEach { manager.createNotificationChannel(it) }
        }
    }
}

/** Sends non-fatal errors to Crashlytics in release builds. */
private class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < android.util.Log.WARN) return
        FirebaseCrashlytics.getInstance().apply {
            log("[$tag] $message")
            t?.let { recordException(it) }
        }
    }
}
