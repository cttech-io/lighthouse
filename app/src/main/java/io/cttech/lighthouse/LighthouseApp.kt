package io.cttech.lighthouse

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.cttech.lighthouse.covers.CoverService
import io.cttech.lighthouse.covers.CoverStore
import io.cttech.lighthouse.data.db.LighthouseDatabase
import io.cttech.lighthouse.library.LibraryRepository
import io.cttech.lighthouse.storage.FlavorStorage
import io.cttech.lighthouse.system.AppSettings

class LighthouseApp : Application() {
    val settings: AppSettings by lazy { AppSettings(this) }

    private val db by lazy { LighthouseDatabase.create(this) }

    val covers: CoverService by lazy { CoverService(this, db.library(), CoverStore(this), settings, cacheDir) }

    val repository: LibraryRepository by lazy {
        // After every scan, pick up the user's own cover images (offline, cheap).
        LibraryRepository(this, db.library(), FlavorStorage, afterScan = { roots -> covers.applyManual(roots) })
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
