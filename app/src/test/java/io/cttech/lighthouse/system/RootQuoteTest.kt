package io.cttech.lighthouse.system

import org.junit.Assert.assertEquals
import org.junit.Test

class RootQuoteTest {
    // What the shell actually sees: the quoted value must come back unchanged, never run.
    private fun sh(script: String) = ProcessBuilder("/bin/sh", "-c", script).start().inputStream.bufferedReader().readText()

    @Test fun awkwardFileNamesStayLiteral() {
        for (name in listOf("plain.gba", "Mario's Game.gba", "a'; touch /tmp/pwned; '.gba", "\$(id) `id` \$HOME.gba", "two  spaces\tand tab")) {
            assertEquals(name + "\n", sh("printf '%s\\n' ${Root.quote(name)}"))
        }
    }
}
