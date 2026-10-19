package io.cttech.lighthouse

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.cttech.lighthouse.data.db.LighthouseDatabase
import io.cttech.lighthouse.library.LibraryRepository
import io.cttech.lighthouse.storage.FlavorStorage
import io.cttech.lighthouse.system.AppSettings

class LighthouseApp : Application() {
    val settings: AppSettings by lazy { AppSettings(this) }

    val repository: LibraryRepository by lazy {
        LibraryRepository(this, LighthouseDatabase.create(this).library(), FlavorStorage)
    }

    override fun onCreate() {
        super.onCreate()
        // Keep the Android tab in sync as apps are installed, updated or removed.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = repository.refreshApps()
            },
            filter,
            ContextCompat.RECEIVER_EXPORTED, // system package broadcasts
        )
    }
}
