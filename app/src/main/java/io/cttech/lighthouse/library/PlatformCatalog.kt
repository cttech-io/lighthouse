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
            "3ds", "3DS", Color(0xFFD81B60),
            // .app is skipped: Azahar can open it, but it's too generic a name to claim for every folder.
            extensions = setOf("3ds", "cci", "cxi", "3dsx", "cia"),
            folderNames = setOf("3ds", "nintendo 3ds", "n3ds", "nintendo - nintendo 3ds"),
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
            "nes", "NES", Color(0xFFE53935),
            extensions = setOf("nes", "unf", "unif", "fds", "zip", "7z"),
            folderNames = setOf("nes", "famicom", "nintendo entertainment system", "nintendo - nintendo entertainment system"),
        ),
        PlatformDef(
            "snes", "SNES", Color(0xFF9575CD),
            extensions = setOf("smc", "sfc", "fig", "swc", "zip", "7z"),
            folderNames = setOf("snes", "sfc", "super nintendo", "super famicom", "super nintendo entertainment system", "nintendo - super nintendo entertainment system"),
        ),
        PlatformDef(
            "megadrive", "Mega Drive", Color(0xFF1E88E5),
            extensions = setOf("md", "gen", "smd", "68k", "bin", "zip", "7z"),
            folderNames = setOf("megadrive", "mega drive", "genesis", "sega genesis", "sega mega drive", "md", "sega - mega drive - genesis"),
        ),
        PlatformDef(
            "mastersystem", "Master System", Color(0xFFD84315),
            extensions = setOf("sms", "zip", "7z"),
            folderNames = setOf("mastersystem", "master system", "sega master system", "sms", "sega - master system - mark iii"),
        ),
        PlatformDef(
            "gamegear", "Game Gear", Color(0xFF00897B),
            extensions = setOf("gg", "zip", "7z"),
            folderNames = setOf("gamegear", "game gear", "sega game gear", "gg", "sega - game gear"),
        ),
        PlatformDef(
            "pcengine", "PC Engine", Color(0xFFFB8C00),
            extensions = setOf("pce", "sgx", "zip", "7z"),
            folderNames = setOf("pcengine", "pc engine", "turbografx-16", "turbografx16", "tg16", "pce", "nec - pc engine - turbografx 16"),
        ),
        PlatformDef(
            "atari2600", "Atari 2600", Color(0xFFA1887F),
            extensions = setOf("a26", "bin", "zip", "7z"),
            folderNames = setOf("atari2600", "atari 2600", "a2600", "atari - 2600"),
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
