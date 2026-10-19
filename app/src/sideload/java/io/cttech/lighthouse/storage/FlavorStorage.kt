package io.cttech.lighthouse.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.provider.Settings
import java.io.File

/** sideload flavour: "All files access" lets us read ROM folders directly and watch them for changes. */
object FlavorStorage : StorageAccess {
    override fun hasAccess(context: Context): Boolean = Environment.isExternalStorageManager()

    override fun accessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    override fun createWatcher(onChange: (String) -> Unit): LibraryWatcher = FileObserverWatcher(onChange)
}

/**
 * One [FileObserver] per directory (inotify isn't recursive). Rebuilt after every rescan, so new
 * sub-folders get watched too. Observers are held strongly: a collected observer stops firing.
 */
private class FileObserverWatcher(private val onChange: (String) -> Unit) : LibraryWatcher {
    private val observers = mutableListOf<FileObserver>()
    private var watchedDirs: Map<String, String> = emptyMap()   // dir path -> platformId
    private val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
        FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF

    @Synchronized
    override fun watch(dirsByPlatform: Map<String, List<File>>) {
        val wanted = linkedMapOf<String, String>()
        for ((platformId, roots) in dirsByPlatform) {
            for (root in roots) walkDirs(root, 0) { dir -> wanted.putIfAbsent(dir.absolutePath, platformId) }
        }
        // Rebuilding tears down inotify watches, which itself emits events; only do it when folders changed.
        if (wanted == watchedDirs) return
        stop()
        watchedDirs = wanted
        for ((path, platformId) in wanted) {
            observers += object : FileObserver(File(path), mask) {
                override fun onEvent(event: Int, name: String?) {
                    // FileObserver also delivers bookkeeping events (e.g. IN_IGNORED when a watch is removed)
                    // regardless of the mask; only real file changes count.
                    if (event and mask == 0) return
                    if (name != null && name.startsWith(".")) return
                    onChange(platformId)
                }
            }.also { it.startWatching() }
        }
    }

    @Synchronized
    override fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        watchedDirs = emptyMap()
    }

    private fun walkDirs(dir: File, depth: Int, visit: (File) -> Unit) {
        if (depth > 5 || !dir.isDirectory || dir.name.startsWith(".")) return
        visit(dir)
        dir.listFiles()?.filter { it.isDirectory }?.forEach { walkDirs(it, depth + 1, visit) }
    }
}
