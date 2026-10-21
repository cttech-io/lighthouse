package io.cttech.lighthouse.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCheckTest {
    private val core = "/data/user/0/com.retroarch.aarch64/cores/mgba_libretro_android.so"

    @Test fun looksThroughInitsRoot() {
        val cmd = GameLauncher.coreCheckCommand(core)
        assertTrue(cmd, cmd.contains("-f '/proc/1/root$core'"))
        // "absent" only when the emulator's data folder itself is visible.
        assertTrue(cmd, cmd.contains("-d '/proc/1/root/data/user/0/com.retroarch.aarch64'"))
    }

    @Test fun unknownWhenTheDataFolderIsHidden() {
        // No /proc/1/root here (as when isolation hides it): must not report the core missing.
        val out = ProcessBuilder("/bin/sh", "-c", GameLauncher.coreCheckCommand(core)).start().inputStream.bufferedReader().readText()
        assertEquals("unknown\n", out)
    }
}
