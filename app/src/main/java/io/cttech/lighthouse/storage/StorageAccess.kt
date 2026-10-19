package io.cttech.lighthouse.storage

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Everything flavour-specific about reading ROM folders lives behind this interface.
 * sideload: "All files access" + real-time watching. play: no broad access (folder picker arrives with setup, M6).
 */
interface StorageAccess {
    /** True when Lighthouse may read and auto-detect ROM folders. */
    fun hasAccess(context: Context): Boolean

    /** Settings screen that grants access, or null when this flavour has none. */
    fun accessIntent(context: Context): Intent?

    fun createWatcher(onChange: (platformId: String) -> Unit): LibraryWatcher
}

/** Notifies when files under watched folders change. Implementations must be cheap to rebuild. */
interface LibraryWatcher {
    fun watch(dirsByPlatform: Map<String, List<File>>)
    fun stop()
}
