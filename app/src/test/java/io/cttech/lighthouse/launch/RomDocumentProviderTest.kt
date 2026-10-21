package io.cttech.lighthouse.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomDocumentProviderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val root get() = File(tmp.root, "storage").apply { mkdirs() }
    private fun rom(path: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText("x") }
    private fun serve(vararg segments: String) = RomDocumentProvider.servable(segments.toList(), root)

    @Test fun servesAFileInsideTheRoot() {
        val f = rom("emulated/0/Games/PSP/Patapon (Europe).iso")
        assertEquals(f.canonicalFile, serve("document", f.path))
    }

    @Test fun refusesAnythingElse() {
        val outside = File(tmp.root, "secret.db").apply { writeText("x") }
        val dir = File(root, "emulated/0/Games").apply { mkdirs() }
        assertNull(serve("document", outside.path))
        assertNull(serve("document", "${root.path}/emulated/0/../../secret.db")) // .. escape
        assertNull(serve("document", dir.path)) // folders
        assertNull(serve("document", "${root.path}/missing.iso"))
        assertNull(serve("tree", rom("a.iso").path)) // wrong shape
        assertNull(serve("document", rom("b.iso").path, "extra"))
    }

    @Test fun refusesSymlinksOutOfTheRoot() {
        val outside = File(tmp.root, "private.db").apply { writeText("x") }
        val link = File(root, "link.iso")
        java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertNull(serve("document", link.path))
    }
}
