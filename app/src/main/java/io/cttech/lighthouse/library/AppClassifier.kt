package io.cttech.lighthouse.library

import android.content.pm.ApplicationInfo

/**
 * Decides whether an Android app is something you "play" (so it belongs in Continue playing)
 * or a utility (Bitwarden, Tailscale, Magisk…). Game apps declare themselves to Android; streaming
 * clients, emulators and PC runners don't, so they're recognised by package.
 */
object AppClassifier {
    private val playPackages = setOf(
        // Streaming clients
        "com.limelight", "com.valvesoftware.steamlink",
        // Emulators
        "dev.eden.eden_emulator", "com.armsx2", "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold",
        "org.dolphinemu.dolphinemu", "com.github.stenzek.duckstation", "io.github.lime3ds.android",
        "org.azahar_emu.azahar", "org.vita3k.emulator", "info.cemu.cemu", "xyz.aethersx2.android",
        // PC runners
        "com.droiddeck.launcher", "app.gamenative", "com.xiaoji.egggame", "com.winlator",
    )
    private val playPrefixes = listOf("com.retroarch")

    fun isGameLike(info: ApplicationInfo): Boolean {
        @Suppress("DEPRECATION")
        val declaredGame = info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        val pkg = info.packageName
        return declaredGame || pkg in playPackages || playPrefixes.any { pkg.startsWith(it) }
    }
}
