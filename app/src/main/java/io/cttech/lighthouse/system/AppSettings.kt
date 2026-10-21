package io.cttech.lighthouse.system

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.cttech.lighthouse.model.HomeLayout
import org.json.JSONObject

enum class HapticStrength { Off, Light, Normal, Strong }

/** What the Home button does while Lighthouse is already showing. */
enum class HomeButtonAction { TopLeft, LastPosition }

/** Games that arrive without local artwork: ask before going online, fetch on its own, or leave them. */
enum class CoverAutoMode { Ask, Always, Never }

/** Where the Wi-Fi network name comes from: Android's location route, root, either, or never shown. */
enum class WifiNameSource { Auto, Location, Root, Off }

enum class CardSize { Small, Medium, Large }

/** HOME dashboard sections, default order. The first visible one gets the large covers. */
val HOME_SECTIONS = listOf("favourites", "continue", "recent", "streaming", "moonlight", "platforms")

/**
 * Small user preferences. Backed by SharedPreferences; exposed as Compose state so the UI reacts instantly,
 * and as [changes] (bumped on every library-affecting write) so view models can rebuild their state.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("lighthouse_settings", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0)
    /** Bumped when tab order/visibility/labels, emulator choices or "counts as game" overrides change. */
    val changes: StateFlow<Int> = _changes
    private fun changed() { _changes.value++ }

    // --- Controls ---------------------------------------------------------------------------------

    var haptics by mutableStateOf(
        prefs.getString(KEY_HAPTIC_STRENGTH, null)?.let { runCatching { HapticStrength.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean(KEY_HAPTICS, true)) HapticStrength.Normal else HapticStrength.Off,
    )
        private set

    val hapticsEnabled: Boolean get() = haptics != HapticStrength.Off

    fun updateHaptics(strength: HapticStrength) {
        haptics = strength
        prefs.edit().putString(KEY_HAPTIC_STRENGTH, strength.name).apply()
    }

    fun setHaptics(enabled: Boolean) = updateHaptics(if (enabled) HapticStrength.Normal else HapticStrength.Off)

    /** Nintendo-style layout: the right face button confirms. Swaps A and B for the whole app. */
    var swapAB by mutableStateOf(prefs.getBoolean(KEY_SWAP_AB, false))
        private set

    fun updateSwapAB(on: Boolean) {
        swapAB = on
        prefs.edit().putBoolean(KEY_SWAP_AB, on).apply()
    }

    var homeButton by mutableStateOf(enumPref(KEY_HOME_BUTTON, HomeButtonAction.TopLeft))
        private set

    fun updateHomeButton(action: HomeButtonAction) {
        homeButton = action
        prefs.edit().putString(KEY_HOME_BUTTON, action.name).apply()
    }

    // --- System -----------------------------------------------------------------------------------

    /** Use root for extras when available. Off = behave exactly like a non-rooted device. */
    var rootExtrasEnabled by mutableStateOf(prefs.getBoolean(KEY_ROOT, true))
        private set

    fun setRootExtras(enabled: Boolean) {
        rootExtrasEnabled = enabled
        Root.enabled = enabled
        prefs.edit().putBoolean(KEY_ROOT, enabled).apply()
    }

    /** First-run setup finished (or skipped). Settings → System can run it again. */
    var setupDone by mutableStateOf(prefs.getBoolean(KEY_SETUP_DONE, false))
        private set

    fun updateSetupDone(done: Boolean) {
        setupDone = done
        prefs.edit().putBoolean(KEY_SETUP_DONE, done).apply()
    }

    /**
     * Setup step to come back to. Setup can restart Lighthouse (becoming the Home app does), and the activity keeps
     * no saved state, so the step is stored; back to 0 when setup ends.
     */
    var setupStep: Int
        get() = prefs.getInt(KEY_SETUP_STEP, 0)
        set(value) { prefs.edit().putInt(KEY_SETUP_STEP, value).apply() }

    var wifiNameSource by mutableStateOf(enumPref(KEY_WIFI_NAME, WifiNameSource.Auto))
        private set

    fun updateWifiNameSource(source: WifiNameSource) {
        wifiNameSource = source
        prefs.edit().putString(KEY_WIFI_NAME, source.name).apply()
    }

    // --- Covers -----------------------------------------------------------------------------------

    /** The user's own SteamGridDB API key (cover art). Empty = SteamGridDB not used. */
    var steamGridDbKey by mutableStateOf(prefs.getString(KEY_SGDB, "").orEmpty())
        private set

    fun updateSteamGridDbKey(key: String) {
        steamGridDbKey = key
        prefs.edit().putString(KEY_SGDB, key).apply()
    }

    var coverAuto by mutableStateOf(enumPref(KEY_COVER_AUTO, CoverAutoMode.Ask))
        private set

    fun updateCoverAuto(mode: CoverAutoMode) {
        coverAuto = mode
        prefs.edit().putString(KEY_COVER_AUTO, mode.name).apply()
    }

    /** Games already offered (or tried) for an online cover, so nobody is asked twice about the same game. */
    fun coverOffered(): Set<String> = prefs.getStringSet(KEY_COVER_OFFERED, emptySet()).orEmpty()

    fun markCoverOffered(keys: Collection<String>) {
        if (keys.isEmpty()) return
        prefs.edit().putStringSet(KEY_COVER_OFFERED, coverOffered() + keys).apply()
    }

    // --- Appearance -------------------------------------------------------------------------------

    /** Selected theme id ("lighthouse" = built-in look). */
    var themeId by mutableStateOf(prefs.getString(KEY_THEME, "lighthouse").orEmpty())
        private set

    fun updateTheme(id: String) {
        themeId = id
        prefs.edit().putString(KEY_THEME, id).apply()
    }

    var cardSize by mutableStateOf(enumPref(KEY_CARD_SIZE, CardSize.Medium))
        private set

    fun updateCardSize(size: CardSize) {
        cardSize = size
        prefs.edit().putString(KEY_CARD_SIZE, size.name).apply()
    }

    /** Carousel or grid on platform tabs. Y on a shelf switches it too, and the choice is kept. */
    var layout by mutableStateOf(enumPref(KEY_LAYOUT, HomeLayout.Carousel))
        private set

    fun updateLayout(l: HomeLayout) {
        layout = l
        prefs.edit().putString(KEY_LAYOUT, l.name).apply()
    }

    /** The stats line at the top of HOME (games, apps, free space, last scan). Off by default. */
    var showHomeStats by mutableStateOf(prefs.getBoolean(KEY_HOME_STATS, false))
        private set

    fun updateShowHomeStats(show: Boolean) {
        showHomeStats = show
        prefs.edit().putBoolean(KEY_HOME_STATS, show).apply()
    }

    /** HOME dashboard sections in display order; ids not listed (new sections) follow in the default order. */
    var homeSectionOrder by mutableStateOf(
        prefs.getString(KEY_HOME_ORDER, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty().let { saved ->
            // A section added in an update goes after its default neighbour, not to the end of a saved order.
            HOME_SECTIONS.filter { it !in saved }.fold(saved.filter { it in HOME_SECTIONS }) { order, id ->
                val before = HOME_SECTIONS.subList(0, HOME_SECTIONS.indexOf(id)).lastOrNull { it in order }
                order.toMutableList().apply { add(if (before == null) 0 else indexOf(before) + 1, id) }
            }
        },
    )
        private set

    fun updateHomeSectionOrder(ids: List<String>) {
        homeSectionOrder = ids
        prefs.edit().putString(KEY_HOME_ORDER, ids.joinToString(",")).apply()
        changed()
    }

    var hiddenHomeSections by mutableStateOf(prefs.getStringSet(KEY_HOME_HIDDEN, emptySet()).orEmpty().toSet())
        private set

    fun setHomeSectionHidden(id: String, hidden: Boolean) {
        hiddenHomeSections = if (hidden) hiddenHomeSections + id else hiddenHomeSections - id
        prefs.edit().putStringSet(KEY_HOME_HIDDEN, hiddenHomeSections).apply()
        changed()
    }

    // --- Platforms (tabs) -------------------------------------------------------------------------

    /** Platform ids in the user's tab order; platforms not listed keep the catalogue order after these. */
    var tabOrder by mutableStateOf(prefs.getString(KEY_TAB_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() })
        private set

    fun updateTabOrder(ids: List<String>) {
        tabOrder = ids
        prefs.edit().putString(KEY_TAB_ORDER, ids.joinToString(",")).apply()
        changed()
    }

    var hiddenTabs by mutableStateOf(prefs.getStringSet(KEY_HIDDEN_TABS, emptySet()).orEmpty().toSet())
        private set

    fun setTabHidden(id: String, hidden: Boolean) {
        hiddenTabs = if (hidden) hiddenTabs + id else hiddenTabs - id
        prefs.edit().putStringSet(KEY_HIDDEN_TABS, hiddenTabs).apply()
        changed()
    }

    /** Custom tab names, e.g. "switch" -> "NS". */
    var tabLabels by mutableStateOf(readMap(KEY_TAB_LABELS))
        private set

    fun updateTabLabel(id: String, label: String?) {
        tabLabels = if (label.isNullOrBlank()) tabLabels - id else tabLabels + (id to label.trim())
        writeMap(KEY_TAB_LABELS, tabLabels)
        changed()
    }

    // --- Emulators --------------------------------------------------------------------------------

    /** Platform id -> launch profile id the user picked (when several emulators are installed). */
    var emulatorChoice by mutableStateOf(readMap(KEY_EMULATORS))
        private set

    fun updateEmulator(platformId: String, profileId: String?) {
        emulatorChoice = if (profileId == null) emulatorChoice - platformId else emulatorChoice + (platformId to profileId)
        writeMap(KEY_EMULATORS, emulatorChoice)
        changed()
    }

    // --- "Counts as a game" overrides ---------------------------------------------------------------

    /** Game key -> true/false, overriding the automatic guess for Continue playing. */
    var gameOverrides by mutableStateOf(readMap(KEY_GAME_OVERRIDES).mapValues { it.value == "1" })
        private set

    fun updateGameOverride(key: String, isGame: Boolean?) {
        gameOverrides = if (isGame == null) gameOverrides - key else gameOverrides + (key to isGame)
        writeMap(KEY_GAME_OVERRIDES, gameOverrides.mapValues { if (it.value) "1" else "0" })
        changed()
    }

    init { Root.enabled = rootExtrasEnabled }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun readMap(key: String): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun writeMap(key: String, map: Map<String, String>) {
        prefs.edit().putString(key, JSONObject(map).toString()).apply()
    }

    private companion object {
        const val KEY_HAPTICS = "haptics_enabled" // pre-M6 on/off, read once for migration
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_SWAP_AB = "swap_ab"
        const val KEY_HOME_BUTTON = "home_button"
        const val KEY_ROOT = "root_extras_enabled"
        const val KEY_SETUP_DONE = "setup_done"
        const val KEY_SETUP_STEP = "setup_step"
        const val KEY_SGDB = "steamgriddb_api_key"
        const val KEY_COVER_AUTO = "cover_auto"
        const val KEY_COVER_OFFERED = "cover_offered"
        const val KEY_THEME = "theme_id"
        const val KEY_TAB_ORDER = "tab_order"
        const val KEY_HIDDEN_TABS = "hidden_tabs"
        const val KEY_TAB_LABELS = "tab_labels"
        const val KEY_EMULATORS = "emulator_choice"
        const val KEY_GAME_OVERRIDES = "game_overrides"
        const val KEY_WIFI_NAME = "wifi_name_source"
        const val KEY_CARD_SIZE = "card_size"
        const val KEY_LAYOUT = "layout"
        const val KEY_HOME_ORDER = "home_section_order"
        const val KEY_HOME_HIDDEN = "home_sections_hidden"
        const val KEY_HOME_STATS = "home_stats"
    }
}

val LocalAppSettings = compositionLocalOf<AppSettings> { error("AppSettings not provided") }
