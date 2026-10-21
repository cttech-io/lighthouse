package io.cttech.lighthouse.launch

import io.cttech.lighthouse.system.readBounded
import java.io.File

/**
 * Files a disc image refers to: the tracks of a .cue sheet, the discs (and their tracks) of an .m3u playlist.
 * An emulator given a content link only gets read access to the files we grant, so these have to go with it.
 * Only existing files inside the image's own folder are returned.
 */
object RomCompanions {
    private const val MAX_SHEET_BYTES = 64 * 1024
    private val cueFile = Regex("""^\s*FILE\s+(?:"([^"]+)"|(\S+))""", RegexOption.IGNORE_CASE)

    fun of(rom: File): List<File> = collect(rom, depth = 0).distinct()

    private fun collect(rom: File, depth: Int): List<File> {
        if (depth > 1) return emptyList()
        val names = when (rom.extension.lowercase()) {
            "cue" -> lines(rom).mapNotNull { line -> cueFile.find(line)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            "m3u" -> lines(rom).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            else -> return emptyList()
        }
        val folder = rom.absoluteFile.parentFile?.canonicalFile ?: return emptyList()
        return names.mapNotNull { inside(folder, it) }.flatMap { listOf(it) + collect(it, depth + 1) }
    }

    private fun lines(file: File): List<String> = file.readBounded(MAX_SHEET_BYTES)?.decodeToString()?.lines().orEmpty()

    private fun inside(folder: File, name: String): File? {
        val file = runCatching { File(folder, name.replace('\\', '/')).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.path.startsWith(folder.path + File.separator) && it.isFile }
    }
}
