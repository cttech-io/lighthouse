package io.cttech.lighthouse.library

import java.io.File

data class ScannedRom(
    val title: String,
    val sortKey: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** Walks platform folders and returns one entry per game. Pure java.io, so it is unit-testable. */
object RomScanner {
    private const val MAX_DEPTH = 5

    /** Folders that hold emulator support files, never games. Matched as whole words in the folder name. */
    private val skipDirWords = setOf("bios", "firmware", "keys", "mods", "mod", "saves", "save", "states", "cheats", "images", "covers", "update", "updates", "dlc")

    private val switchUpdate = Regex("""\[v[1-9]\d*]|\bv[1-9]\d{3,}\b|\bupdate\b|\bdlc\b""", RegexOption.IGNORE_CASE)

    fun scan(platform: PlatformDef, dirs: List<File>): List<ScannedRom> {
        val out = linkedMapOf<String, ScannedRom>()     // sortKey -> first occurrence wins (dedupes copies)
        for (dir in dirs) walk(platform, dir, 0, out)
        return out.values.sortedBy { it.sortKey }
    }

    private fun walk(platform: PlatformDef, dir: File, depth: Int, out: MutableMap<String, ScannedRom>) {
        if (depth > MAX_DEPTH) return
        val entries = dir.listFiles() ?: return
        val names = entries.mapTo(HashSet()) { it.name.lowercase() }
        for (f in entries.sortedBy { it.name.lowercase() }) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (!isSkippedDir(f.name)) walk(platform, f, depth + 1, out)
                continue
            }
            val ext = f.extension.lowercase()
            if (ext !in platform.extensions) continue
            if (platform.skipUpdatesAndDlc && switchUpdate.containsMatchIn(f.name)) continue
            // A .bin with a matching .cue is a track of that disc, not a game of its own.
            if (ext == "bin" && "${f.nameWithoutExtension.lowercase()}.cue" in names) continue
            val title = TitleCleaner.clean(f.name)
            val key = TitleCleaner.sortKey(title)
            out.putIfAbsent(key, ScannedRom(title, key, f.absolutePath, f.length(), f.lastModified()))
        }
    }

    internal fun isSkippedDir(name: String): Boolean {
        val words = name.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        return words.any { it in skipDirWords }
    }
}
