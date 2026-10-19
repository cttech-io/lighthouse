package io.cttech.lighthouse.storage

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * play flavour: no "All files access". Until folder picking arrives with first-run setup (M6), only
 * Android apps are listed; ROM folders will then be picked via the Storage Access Framework and
 * rescanned on resume / periodically instead of watched.
 */
object FlavorStorage : StorageAccess {
    override fun hasAccess(context: Context): Boolean = false
    override fun accessIntent(context: Context): Intent? = null
    override fun createWatcher(onChange: (String) -> Unit): LibraryWatcher = object : LibraryWatcher {
        override fun watch(dirsByPlatform: Map<String, List<File>>) = Unit
        override fun stop() = Unit
    }
}
