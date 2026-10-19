package io.cttech.lighthouse.model

import androidx.compose.ui.graphics.Color

/** A system shown as a tab, e.g. "Switch". [accent] tints the placeholder background until themes land. */
data class Platform(
    val id: String,
    val tabLabel: String,
    val accent: Color,
)

data class Game(
    val id: String,
    val platformId: String,
    val title: String,
    val year: Int? = null,
    val subtitle: String? = null,
)

/** A home tab: either a real platform or a virtual view such as Favourites. */
sealed interface HomeTab {
    val key: String
    val label: String

    data object Favourites : HomeTab {
        override val key = "favs"
        override val label = "Favs"
    }

    data class PlatformTab(val platform: Platform) : HomeTab {
        override val key = "platform:${platform.id}"
        override val label = platform.tabLabel
    }
}

enum class HomeLayout { Carousel, Grid }
