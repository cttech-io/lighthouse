package io.cttech.lighthouse.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cttech.lighthouse.library.PlatformCatalog
import io.cttech.lighthouse.system.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The platform order Settings and the tab bar share: catalogue order, then the user's saved order and labels on top. */
@RunWith(AndroidJUnit4::class)
class OrderedPlatformsTest {
    private lateinit var settings: AppSettings

    @Before
    fun fresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lighthouse_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = AppSettings(context)
    }

    @Test
    fun withNothingSaved_followsTheCatalogue() {
        assertEquals(PlatformCatalog.all.map { it.id }, orderedPlatforms(settings).map { it.id })
    }

    @Test
    fun aSavedOrderComesFirst_andNewPlatformsFollowInCatalogueOrder() {
        settings.updateTabOrder(listOf("n64", "ps1"))

        val ids = orderedPlatforms(settings).map { it.id }

        assertEquals(listOf("n64", "ps1"), ids.take(2))
        assertEquals(PlatformCatalog.all.map { it.id }.filter { it !in setOf("n64", "ps1") }, ids.drop(2))
    }

    @Test
    fun aCustomLabelReplacesTheTabLabel() {
        settings.updateTabLabel("ps1", "PlayStation")

        assertEquals("PlayStation", orderedPlatforms(settings).first { it.id == "ps1" }.tabLabel)
    }
}
