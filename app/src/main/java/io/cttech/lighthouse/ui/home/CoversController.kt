package io.cttech.lighthouse.ui.home

import android.app.Application
import android.net.Uri
import io.cttech.lighthouse.covers.CoverService
import io.cttech.lighthouse.covers.SteamGridDb
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.system.AppSettings
import io.cttech.lighthouse.system.CoverAutoMode
import io.cttech.lighthouse.system.readBounded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything about cover art that the screens drive: the picker, the offer after a scan, and the bulk fetches. */
class CoversController(
    private val app: Application,
    private val service: CoverService,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    val job = service.job
    val offer = service.offer

    private val _pickerFor = MutableStateFlow<Game?>(null)
    /** Game whose cover picker is open. */
    val pickerFor: StateFlow<Game?> = _pickerFor

    fun openPicker(game: Game) { _pickerFor.value = game }
    fun closePicker() { _pickerFor.value = null }

    fun hasSteamGridDbKey(): Boolean = service.steamGridDb() != null
    fun steamAppIdFor(game: Game): Int? = game.path?.let(service::steamAppIdFor)

    /** Checks a SteamGridDB key with one search call. */
    suspend fun testSteamGridDbKey(key: String): Boolean = withContext(Dispatchers.IO) { SteamGridDb(key).isValid() }

    suspend fun searchCovers(term: String) = withContext(Dispatchers.IO) { service.steamGridDb()?.search(term).orEmpty() }

    suspend fun coverGrids(gameId: Int? = null, steamAppId: Int? = null) =
        withContext(Dispatchers.IO) { service.steamGridDb()?.grids(gameId, steamAppId).orEmpty() }

    suspend fun applyCover(game: Game, url: String): Boolean = service.applyPicked(game.key, url = url).also { if (it) closePicker() }

    suspend fun applyCoverImage(game: Game, uri: Uri): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBounded() } }.getOrNull()
        } ?: return false
        return service.applyPicked(game.key, bytes = bytes).also { if (it) closePicker() }
    }

    /** Removes the cover and closes the picker. */
    fun removeCover(game: Game) {
        scope.launch { service.removeCover(game.key) }
        _pickerFor.value = null
    }

    fun findMissing() { scope.launch { service.findMissing() } }
    fun redownloadAll() { scope.launch { service.redownloadAll() } }

    fun answerOffer(fetch: Boolean, always: Boolean = false) {
        if (always) settings.updateCoverAuto(CoverAutoMode.Always)
        scope.launch { service.answerOffer(fetch) }
    }
}
