package io.cttech.lighthouse.launch

import io.cttech.lighthouse.library.MoonlightApp
import io.cttech.lighthouse.library.MoonlightSource
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.system.Root
import io.cttech.lighthouse.system.readIdText
import io.cttech.lighthouse.library.PlatformCatalog
import java.io.File

/** What happened when we tried to start a game; anything but [Started] is shown to the user with a fix. */
sealed interface LaunchResult {
    data object Started : LaunchResult
    /** No profile for this platform has its app installed. */
    data class EmulatorMissing(val names: List<String>) : LaunchResult
    /** e.g. RetroArch without storage permission: it can't open ROMs by path. */
    data class NeedsStoragePermission(val profile: LaunchProfile, val pkg: String) : LaunchResult
    data class CoreMissing(val profile: LaunchProfile, val corePath: String) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

/** One emulator for a platform as Settings shows it: installed?, can it read ROMs?, is its core there? */
data class EmulatorStatus(
    val profile: LaunchProfile,
    /** Installed package, or null when not installed. */
    val pkg: String?,
    val storageOk: Boolean,
    /** true/false when checkable (root), null when it can't be checked. */
    val corePresent: Boolean?,
)

/** [preferredProfile]: platform id -> the profile id the user chose in Settings, if any. */
class GameLauncher(private val context: Context, private val preferredProfile: (String) -> String? = { null }) {
    private val pm = context.packageManager

    /** Installed profile used for [platformId]: the user's choice if installed, else the first installed one. */
    fun activeProfile(platformId: String): Pair<LaunchProfile, String>? {
        val installed = LaunchProfiles.forPlatform(context, platformId)
            .mapNotNull { p -> p.packages.firstOrNull(::isInstalled)?.let { p to it } }
        val chosen = preferredProfile(platformId)
        return installed.firstOrNull { it.first.id == chosen } ?: installed.firstOrNull()
    }

    /** Every known emulator for [platformId], installed or not, for Settings → Emulators. */
    suspend fun status(platformId: String): List<EmulatorStatus> =
        LaunchProfiles.forPlatform(context, platformId).map { p ->
            val pkg = p.packages.firstOrNull(::isInstalled)
            val core = if (pkg != null && p.core != null) {
                Root.run("if [ -f ${Root.quote(p.core.replace("{package}", pkg))} ]; then echo present; else echo absent; fi")
                    ?.let { it.contains("present") }
            } else if (p.core == null) true else null
            EmulatorStatus(p, pkg, storageOk = pkg == null || !p.needsStorage || canReadStorage(pkg), corePresent = core)
        }

    /** Android apps launch directly; ROMs and shortcuts go through the platform's launch profile. */
    suspend fun launch(game: Game): LaunchResult {
        game.packageName?.let { pkg ->
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return LaunchResult.Failed("${game.title} can't be opened")
            return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (game.platformId == PlatformCatalog.SHORTCUTS) {
            val (pkg, id) = game.key.substringAfter('|').split('/', limit = 2).takeIf { it.size == 2 } ?: return LaunchResult.Failed("Broken shortcut")
            return if (io.cttech.lighthouse.library.PinnedShortcuts.launch(context, pkg, id)) LaunchResult.Started
            else LaunchResult.Failed("${game.title} couldn't be opened. Is Lighthouse still the default Home app?")
        }
        if (game.platformId == PlatformCatalog.MOONLIGHT) {
            if (!MoonlightSource.installed(context)) return LaunchResult.EmulatorMissing(listOf("Moonlight"))
            val locator = game.key.substringAfter('|')
            // The PC name and HDR flag come from the last read; without one, Moonlight looks the PC up by its id.
            val app = MoonlightSource.app(locator) ?: MoonlightApp(
                locator.substringBefore('/'), "", locator.substringAfter('/').toIntOrNull() ?: return LaunchResult.Failed("Unknown Moonlight game"),
                game.title, hdr = false,
            )
            Log.i(TAG, "launch ${game.title} via Moonlight on ${app.hostName} (${app.hostUuid}/${app.appId})")
            return start(MoonlightSource.launchIntent(app))
        }
        val path = game.path ?: return LaunchResult.Failed("No file for ${game.title}")
        val (profile, pkg) = activeProfile(game.platformId)
            ?: return LaunchResult.EmulatorMissing(LaunchProfiles.forPlatform(context, game.platformId).map { it.label })

        if (profile.needsStorage && !canReadStorage(pkg)) return LaunchResult.NeedsStoragePermission(profile, pkg)
        profile.core?.let { c ->
            val core = c.expand(pkg, path)
            // Only checkable with root (it's inside the emulator's private data); without root, let it try.
            val check = Root.run("if [ -f ${Root.quote(core)} ]; then echo present; else echo absent; fi")
            if (check?.contains("absent") == true) return LaunchResult.CoreMissing(profile, core)
        }

        val intent = Intent(profile.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (profile.activity != null) intent.component = ComponentName(pkg, profile.activity) else intent.setPackage(pkg)
        when (profile.rom) {
            RomDelivery.CONTENT -> {
                val uri = romUri(File(path))
                intent.setDataAndType(uri, profile.mime ?: "application/octet-stream")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            RomDelivery.EXTRAS -> Unit
            RomDelivery.STEAM_APP_ID -> {
                // A shortcut file holds one decimal game ID. Steam IDs fit an Int (GameNative reads an Int);
                // DroidDeck's own added games use the unsigned 64-bit range, which it accepts as a String.
                val file = File(path)
                val id = file.readIdText()?.takeIf { it.isNotEmpty() && it.length <= 20 && it.all(Char::isDigit) && !it.startsWith("0") }
                    ?: return LaunchResult.Failed("${file.name} doesn't contain a game ID")
                id.toIntOrNull()?.let { intent.putExtra("app_id", it) } ?: intent.putExtra("app_id", id)
                // GameNative also writes .epic/.gog/.amazon/.pcgame files for its other stores.
                val source = PlatformCatalog.GAMENATIVE_SOURCES[file.extension.lowercase()] ?: "STEAM"
                intent.putExtra("game_source", source)
            }
        }
        profile.extras.forEach { (k, v) -> intent.putExtra(k, v.expand(pkg, path)) }
        Log.i(TAG, "launch ${game.title} via ${profile.id} ($pkg): $intent extras=${intent.extras?.keySet()}")
        return start(intent)
    }

    /** Opens the emulator's app-info page so the user can allow storage ("Files and media"). */
    fun openAppInfo(pkg: String) {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Root extra: grant an emulator storage access in one step (only after the user asks). */
    suspend fun grantStorageWithRoot(pkg: String): Boolean =
        Root.run(
            "pm grant $pkg ${Manifest.permission.READ_EXTERNAL_STORAGE}; " +
                "pm grant $pkg ${Manifest.permission.WRITE_EXTERNAL_STORAGE}; " +
                "appops set $pkg READ_EXTERNAL_STORAGE allow; appops set $pkg WRITE_EXTERNAL_STORAGE allow",
        ) != null && canReadStorage(pkg)

    private fun canReadStorage(pkg: String): Boolean =
        pm.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, pkg) == PackageManager.PERMISSION_GRANTED

    private fun isInstalled(pkg: String): Boolean = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    private fun romUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.roms", file)

    private fun String.expand(pkg: String, path: String) = this
        .replace("{path}", path)
        .replace("{package}", pkg)
        .replace("{ime}", Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())

    private fun start(intent: Intent): LaunchResult = try {
        context.startActivity(intent)
        LaunchResult.Started
    } catch (e: ActivityNotFoundException) {
        LaunchResult.Failed("The emulator didn't accept the game (${e.message})")
    } catch (e: SecurityException) {
        LaunchResult.Failed("Android blocked the launch (${e.message})")
    }

    private companion object { const val TAG = "LighthouseLaunch" }
}
