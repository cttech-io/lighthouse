package io.cttech.lighthouse.model

import io.cttech.lighthouse.library.PlatformDef

/** UI model for a launchable tile. [key] is stable across rescans and used for favourites and list keys. */
data class Game(
    val key: String,
    val platformId: String,
    val title: String,
    val subtitle: String? = null,
    val year: Int? = null,
    /** Set for Android apps: shows the app icon and launches the package. */
    val packageName: String? = null,
    val path: String? = null,
)

/** A home tab: either a real platform or a virtual view such as Favourites. */
sealed interface HomeTab {
    val key: String
    val label: String

    /** The dashboard: continue playing, favourites, recently added, streaming, platforms. */
    data object Home : HomeTab {
        override val key = "home"
        override val label = "Home"
    }

    data class PlatformTab(val platform: PlatformDef) : HomeTab {
        override val key = "platform:${platform.id}"
        override val label = platform.tabLabel
    }
}

enum class HomeLayout { Carousel, Grid }

/** One row on the Home dashboard. [hero] rows use larger covers. */
data class HomeRow(val id: String, val title: String, val items: List<HomeItem>, val hero: Boolean = false)

sealed interface HomeItem {
    val key: String

    data class GameItem(val game: Game) : HomeItem {
        override val key get() = game.key
    }

    /** Shortcut to a platform tab, with its game count. */
    data class PlatformItem(val tabIndex: Int, val platform: PlatformDef, val count: Int) : HomeItem {
        override val key get() = "platform:${platform.id}"
    }
}
