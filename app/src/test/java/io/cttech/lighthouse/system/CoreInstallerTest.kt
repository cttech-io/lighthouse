package io.cttech.lighthouse.system

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreInstallerTest {
    private val elf = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 2, 3, 4)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun parsesRetroArchCorePaths() {
        val path = "/data/user/0/com.retroarch.aarch64/cores/snes9x_libretro_android.so"
        assertEquals("com.retroarch.aarch64" to "snes9x_libretro_android.so", CoreInstaller.parse(path))
    }

    @Test fun refusesPathsThatAreNotCores() {
        assertNull(CoreInstaller.parse("/sdcard/cores/snes9x_libretro_android.so"))
        assertNull(CoreInstaller.parse("/data/user/0/com.retroarch/cores/../evil.so"))
        assertNull(CoreInstaller.parse("/data/user/0/com.retroarch/cores/snes9x.so"))
        assertNull(CoreInstaller.parse("/data/user/0/com.retroarch; rm -rf x/cores/a_libretro_android.so"))
    }

    @Test fun picksTheBuildForTheAppsBitness() {
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/snes9x_libretro_android.so.zip",
            CoreInstaller.downloadUrl("com.retroarch.aarch64", "snes9x_libretro_android.so"),
        )
        assertTrue(CoreInstaller.downloadUrl("com.retroarch", "snes9x_libretro_android.so").contains("/armeabi-v7a/"))
    }

    @Test fun extractsOnlyTheExpectedLibrary() {
        val file = "snes9x_libretro_android.so"
        assertArrayEquals(elf, CoreInstaller.extract(zip("readme.txt" to byteArrayOf(1), file to elf), file))
    }

    @Test fun ignoresEntriesWithOtherNamesIncludingPathTricks() {
        val file = "snes9x_libretro_android.so"
        assertNull(CoreInstaller.extract(zip("../$file" to elf, "x/$file" to elf), file))
    }

    @Test fun rejectsAFileThatIsNotANativeLibrary() {
        val file = "snes9x_libretro_android.so"
        assertNull(CoreInstaller.extract(zip(file to "<html>captive portal</html>".toByteArray()), file))
    }

    @Test fun rejectsNonZipData() {
        assertNull(CoreInstaller.extract("not a zip".toByteArray(), "snes9x_libretro_android.so"))
    }

    @Test fun installCommandStagesThenRenamesIntoRetroArchsFolder() {
        val cmd = CoreInstaller.installCommand("com.retroarch.aarch64", "snes9x_libretro_android.so", "/data/user/0/io.cttech.lighthouse/cache/cores/snes9x_libretro_android.so")
        val dir = "/proc/1/root/data/user/0/com.retroarch.aarch64/cores"
        assertTrue(cmd, cmd.contains("cp '/data/user/0/io.cttech.lighthouse/cache/cores/snes9x_libretro_android.so' '$dir/.snes9x_libretro_android.so.part'"))
        assertTrue(cmd, cmd.contains("chown \$(stat -c %u:%g '$dir') '$dir/.snes9x_libretro_android.so.part'"))
        assertTrue(cmd, cmd.endsWith("mv '$dir/.snes9x_libretro_android.so.part' '$dir/snes9x_libretro_android.so'"))
    }
}
