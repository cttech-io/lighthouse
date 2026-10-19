package io.cttech.lighthouse.library

import java.io.File

/**
 * Finds platform folders on storage roots, e.g. /storage/XXXX-XXXX/Games/PS2 or /sdcard/ROMs/gba.
 * Looks in each root and in conventional container folders ("Games", "ROMs", "Emulation"), trimming
 * names because real-world folders can carry stray spaces ("Games ").
 */
object FolderDetector {
    private val containers = setOf("games", "roms", "emulation", "emulators")

    data class Found(val platformId: String, val dir: File)

    fun detect(roots: List<File>): List<Found> {
        val found = mutableListOf<Found>()
        for (root in roots) {
            val bases = listOf(root) + root.subdirs().filter { it.name.trim().lowercase() in containers }
            for (base in bases) {
                for (dir in base.subdirs()) {
                    PlatformCatalog.matchFolder(dir.name)?.let { found += Found(it.id, dir) }
                }
            }
        }
        return found.distinctBy { it.dir.absolutePath }
    }

    /**
     * Best guess for a folder the user picked: its name ("PS2", "Game Boy Advance"…), else the platform
     * whose file types are most common inside it (ambiguous types like .zip don't count). Null = no idea.
     */
    fun guessPlatform(dir: File): String? {
        PlatformCatalog.matchFolder(dir.name)?.let { return it.id }
        val shared = PlatformCatalog.all.flatMap { it.extensions }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val counts = mutableMapOf<String, Int>()
        dir.walkTopDown().maxDepth(2).filter { it.isFile }.take(500).forEach { f ->
            val ext = f.extension.lowercase()
            if (ext in shared) return@forEach
            PlatformCatalog.all.firstOrNull { ext in it.extensions }?.let { counts.merge(it.id, 1, Int::plus) }
        }
        return counts.maxByOrNull { it.value }?.key
    }

    private fun File.subdirs(): List<File> =
        listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
}
