package io.cttech.lighthouse.launch

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

class GameLauncher(private val context: Context) {
    private val pm = context.packageManager

    /** Android apps launch directly; ROMs and shortcuts go through the platform's launch profile. */
    suspend fun launch(game: Game): LaunchResult {
        game.packageName?.let { pkg ->
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return LaunchResult.Failed("${game.title} can't be opened")
            return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val path = game.path ?: return LaunchResult.Failed("No file for ${game.title}")
        val profiles = LaunchProfiles.forPlatform(context, game.platformId)
        val (profile, pkg) = profiles.firstNotNullOfOrNull { p -> p.packages.firstOrNull(::isInstalled)?.let { p to it } }
            ?: return LaunchResult.EmulatorMissing(profiles.map { it.label })

        if (profile.needsStorage && !canReadStorage(pkg)) return LaunchResult.NeedsStoragePermission(profile, pkg)
        profile.core?.let { c ->
            val core = c.expand(pkg, path)
            // Only checkable with root (it's inside the emulator's private data); without root, let it try.
            val check = Root.run("if [ -f '$core' ]; then echo present; else echo absent; fi")
            if (check?.contains("absent") == true) return LaunchResult.CoreMissing(profile, core)
        }

        val intent = Intent(profile.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (profile.activity != null) intent.component = ComponentName(pkg, profile.activity) else intent.setPackage(pkg)
        when (profile.rom) {
            RomDelivery.CONTENT -> {
                val uri = romUri(File(path))
                intent.setDataAndType(uri, profile.mime ?: "application/octet-stream")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            RomDelivery.EXTRAS -> Unit
            RomDelivery.STEAM_APP_ID -> {
                val appId = File(path).readText().trim().toIntOrNull()
                    ?: return LaunchResult.Failed("${File(path).name} doesn't contain a Steam app ID")
                intent.putExtra("app_id", appId).putExtra("game_source", "STEAM")
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
