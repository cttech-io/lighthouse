package io.cttech.lighthouse.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.HomeItem
import io.cttech.lighthouse.model.HomeRow
import io.cttech.lighthouse.system.AppSettings
import io.cttech.lighthouse.system.LocalAppSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coming back to HOME must put focus back on the remembered item, even when its row or its place in the row
 * is off screen (lazy lists only compose what is visible). Before the fix, returning with a lower row
 * remembered left nothing focused and the controller did nothing.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class HomeDashboardFocusTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun game(row: String, n: Int) = Game(key = "$row-$n", platformId = "ps1", title = "$row game $n")
    private fun row(id: String, count: Int, hero: Boolean = false) =
        HomeRow(id, id, (1..count).map { HomeItem.GameItem(game(id, it)) }, hero)

    // Taller than the screen, and each row longer than the screen is wide.
    private val rows = listOf(row("favourites", 3, hero = true), row("recent", 40), row("extra", 40), row("platforms", 40))

    private val actions = CardActions(Color.Gray, emptySet(), {}, {}, {}, {})
    private val focusedKeys = mutableListOf<String>()

    private fun show(rememberedKey: String?, rows: List<HomeRow> = this.rows) {
        val settings = AppSettings(ApplicationProvider.getApplicationContext())
        compose.setContent {
            // The Portal is driven by a controller, i.e. keyboard input mode. In touch mode (the test default)
            // requestFocus() is refused, which would make every restore look like a failure.
            LocalInputModeManager.current.requestInputMode(InputMode.Keyboard)
            CompositionLocalProvider(LocalAppSettings provides settings) {
                HomeDashboard(
                    rows = rows,
                    statusLine = "",
                    rememberedKey = rememberedKey,
                    actions = actions,
                    onFocusedKey = { focusedKeys += it },
                    onOpenPlatform = {},
                    refocus = false,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun noMemory_focusesTopLeft() {
        show(rememberedKey = null)

        compose.onNodeWithText("favourites game 1").assertIsFocused()
    }

    @Test
    fun rememberedItemInVisibleRow_isFocused() {
        show(rememberedKey = "favourites|favourites-2")

        compose.onNodeWithText("favourites game 2").assertIsFocused()
    }

    @Test
    fun rememberedItemInLowerRow_scrollsIntoViewAndTakesFocus() {
        show(rememberedKey = "platforms|platforms-1")

        compose.waitUntil(5_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("platforms|platforms-1", focusedKeys.last())
    }

    @Test
    fun rememberedItemFarRightInRow_scrollsIntoViewAndTakesFocus() {
        show(rememberedKey = "recent|recent-38")

        compose.waitUntil(5_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("recent|recent-38", focusedKeys.last())
    }

    @Test
    fun rememberedItemThatNoLongerExists_fallsBackToTopLeft() {
        show(rememberedKey = "recent|gone")

        compose.onNodeWithText("favourites game 1").assertIsFocused()
    }

    @Test
    fun sameGameInTwoRows_restoresTheRowItWasIn() {
        val shared = Game(key = "ps1|shared", platformId = "ps1", title = "Shared")
        val rows = listOf(
            HomeRow("favourites", "favourites", listOf(HomeItem.GameItem(shared)), hero = true),
            HomeRow("recent", "recent", listOf(HomeItem.GameItem(shared))),
        )
        show(rememberedKey = "recent|ps1|shared", rows = rows)

        compose.waitUntil(5_000) { focusedKeys.isNotEmpty() }
        assertEquals("recent|ps1|shared", focusedKeys.last())
    }
}
