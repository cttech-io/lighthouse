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

    private fun File.subdirs(): List<File> =
        listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
}
