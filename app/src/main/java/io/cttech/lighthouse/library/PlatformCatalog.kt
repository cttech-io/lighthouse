package io.cttech.lighthouse.library

import androidx.compose.ui.graphics.Color

/**
 * A system Lighthouse knows how to find games for.
 * [folderNames] are matched (case-insensitive, trimmed) against folder names under Games/ROMs roots.
 */
data class PlatformDef(
    val id: String,
    val tabLabel: String,
    val accent: Color,
    val extensions: Set<String> = emptySet(),
    val folderNames: Set<String> = emptySet(),
    /** Switch-style dumps ship updates/DLC next to base games; those files are skipped. */
    val skipUpdatesAndDlc: Boolean = false,
)

object PlatformCatalog {
    const val ANDROID = "android"
    const val DROIDDECK = "droiddeck"
    const val GAMENATIVE = "gamenative"
    const val MOONLIGHT = "moonlight"
    const val SHORTCUTS = "shortcuts"

    /** GameNative export file extension -> the game_source its launch intent expects. */
    val GAMENATIVE_SOURCES = mapOf("steam" to "STEAM", "epic" to "EPIC", "gog" to "GOG", "amazon" to "AMAZON", "pcgame" to "CUSTOM_GAME")

    val all: List<PlatformDef> = listOf(
        PlatformDef(ANDROID, "Android", Color(0xFF3DDC84)),
        PlatformDef(
            "switch", "Switch", Color(0xFFFF4554),
            extensions = setOf("xci", "nsp", "nca", "nro"),
            folderNames = setOf("switch", "ns", "nintendo switch"),
            skipUpdatesAndDlc = true,
        ),
        PlatformDef(
            "ps2", "PS2", Color(0xFF2F6BFF),
            extensions = setOf("iso", "chd", "cso", "zso", "cue", "bin", "gz"),
            folderNames = setOf("ps2", "playstation 2", "playstation2", "sony - playstation 2"),
        ),
        PlatformDef(
            "ps1", "PS1", Color(0xFFB4ACC0),
            // Not .bin: a .cue (or .m3u for multi-disc games) names its tracks, so listing both would show games twice.
            extensions = setOf("cue", "chd", "pbp", "m3u", "iso"),
            folderNames = setOf("ps1", "psx", "playstation", "playstation 1", "sony - playstation"),
        ),
        PlatformDef(
            "psp", "PSP", Color(0xFF00B8D4),
            extensions = setOf("iso", "cso", "chd", "pbp", "elf"),
            folderNames = setOf("psp", "playstation portable", "sony - playstation portable"),
        ),
        PlatformDef(
            "gba", "GBA", Color(0xFF7A5CFF),
            extensions = setOf("gba", "zip", "7z"),
            folderNames = setOf("gba", "game boy advance", "gameboy advance", "nintendo - game boy advance"),
        ),
        PlatformDef(
            "gbc", "GBC", Color(0xFF26C6A6),
            extensions = setOf("gbc", "gb", "zip", "7z"),
            folderNames = setOf("gbc", "gb", "game boy color", "gameboy color", "game boy", "gameboy"),
        ),
        PlatformDef(
            "n64", "N64", Color(0xFFFFB300),
            extensions = setOf("z64", "n64", "v64", "zip", "7z"),
            folderNames = setOf("n64", "nintendo 64"),
        ),
        PlatformDef(
            "droiddeck", "DroidDeck", Color(0xFF1A9FFF),
            extensions = setOf("steam"),
            folderNames = setOf("droiddeck"),
        ),
        PlatformDef(
            "gamenative", "GameNative", Color(0xFFB0BEC5),
            // One file per installed game, written by GameNative's frontend sync (or "Export for frontend").
            extensions = GAMENATIVE_SOURCES.keys,
            folderNames = setOf("gamenative", "gamenative shortcuts"),
        ),
        // Games on your PCs, read from Moonlight (no folders: see MoonlightSource).
        PlatformDef(MOONLIGHT, "Moonlight", Color(0xFF8C7BFF)),
        // "Create shortcut" / "Add to home screen" from any app (see PinnedShortcuts).
        PlatformDef(SHORTCUTS, "Shortcuts", Color(0xFFFF8A65)),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String): PlatformDef? = byId[id]
    fun order(id: String): Int = all.indexOfFirst { it.id == id }.let { if (it < 0) Int.MAX_VALUE else it }

    /** Platform whose folder name matches [dirName], if any. */
    fun matchFolder(dirName: String): PlatformDef? {
        val n = dirName.trim().lowercase()
        return all.firstOrNull { n in it.folderNames }
    }
}
