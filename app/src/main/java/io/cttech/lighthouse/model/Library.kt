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

    data object Favourites : HomeTab {
        override val key = "favs"
        override val label = "Favs"
    }

    data class PlatformTab(val platform: PlatformDef) : HomeTab {
        override val key = "platform:${platform.id}"
        override val label = platform.tabLabel
    }
}

enum class HomeLayout { Carousel, Grid }
