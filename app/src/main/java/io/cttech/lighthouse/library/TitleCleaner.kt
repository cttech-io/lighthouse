package io.cttech.lighthouse.library

/**
 * Turns a ROM file name into a display title, e.g.
 * "Simpsons, The - Hit & Run (Europe, Australia) (En,Fr,De,Es).iso" -> "The Simpsons: Hit & Run".
 */
object TitleCleaner {
    private val tagGroups = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")
    private val trailingArticle = Regex("""^(.+?), (The|A|An)\b(.*)$""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")

    fun clean(fileName: String): String {
        var t = fileName.substringBeforeLast('.', fileName)
        t = t.replace("_ ", ": ")            // file systems can't store ':' -> "Halo_ The Master Chief"
        t = t.replace(tagGroups, "")          // (USA) (v3.00) [!] [01007EF00011E800]
        t = t.replace('™', ' ').replace('®', ' ')
        t = t.replace(spaces, " ").trim()
        trailingArticle.matchEntire(t)?.let { m ->  // "Simpsons, The - Hit & Run" -> "The Simpsons - Hit & Run"
            val (name, article, rest) = m.destructured
            t = "$article $name$rest"
        }
        t = t.replace(" - ", ": ")             // No-Intro subtitle separator
        return t.replace(spaces, " ").trim().ifEmpty { fileName }
    }

    /** Key for sorting and de-duplicating: lower case, leading article dropped, punctuation stripped. */
    fun sortKey(title: String): String =
        // Strip accents so "Pokémon" (SteamGridDB) and "Pokemon" (No-Intro file names) compare equal.
        java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .removePrefix("the ").removePrefix("a ").removePrefix("an ")
            .replace(Regex("""[^\p{L}\p{N} ]"""), "")
            .replace(spaces, " ")
            .trim()
}
