package io.cttech.lighthouse.launch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LaunchProfilesAssetTest {
    private val profiles = JSONObject(File("src/main/assets/launch_profiles.json").readText()).getJSONArray("profiles")
        .let { a -> List(a.length()) { a.getJSONObject(it) } }

    private fun cores(p: JSONObject): List<String> =
        p.optJSONArray("core")?.let { a -> List(a.length()) { a.getString(it) } } ?: listOfNotNull(p.optString("core").ifEmpty { null })

    @Test fun coreExtrasHaveCores() {
        profiles.filter { p -> p.optJSONObject("extras")?.toString()?.contains("{core}") == true }
            .forEach { p -> assertTrue(p.getString("id"), cores(p).isNotEmpty()) }
    }

    @Test fun n64UsesTheCoresRetroArchShipsOnAndroid() {
        // RetroArch's Android downloader only has the GLES3 and GLES2 builds of Mupen64Plus-Next.
        val n64 = profiles.single { it.getString("id") == "retroarch-mupen64plus" }
        assertEquals(
            listOf("mupen64plus_next_gles3_libretro_android.so", "mupen64plus_next_gles2_libretro_android.so"),
            cores(n64).map { it.substringAfterLast('/') },
        )
    }
}
