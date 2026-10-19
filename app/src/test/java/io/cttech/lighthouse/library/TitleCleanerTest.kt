package io.cttech.lighthouse.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanerTest {
    private fun check(file: String, expected: String) = assertEquals(expected, TitleCleaner.clean(file))

    @Test fun noIntroWithRegionsAndTrailingArticle() =
        check("Simpsons, The - Hit & Run (Europe, Australia) (En,Fr,De,Es).iso", "The Simpsons: Hit & Run")

    @Test fun versionTag() = check("Grand Theft Auto - San Andreas (USA) (v3.00).iso", "Grand Theft Auto: San Andreas")

    @Test fun squareBracketTags() = check("Pokemon - Yellow Version (UE) [C][!].gbc", "Pokemon: Yellow Version")

    @Test fun underscoreStandsInForColon() = check("Halo_ The Master Chief Collection.steam", "Halo: The Master Chief Collection")

    @Test fun trademarkStripped() = check("Life is Strange™.steam", "Life is Strange")

    @Test fun plainName() = check("The Legend of Zelda Breath of the Wild.xci", "The Legend of Zelda Breath of the Wild")

    @Test fun hackWithParens() = check("Pokemon Ultra Violet (1.22) LSA (Fire Red Hack).gba", "Pokemon Ultra Violet LSA")

    @Test fun sortKeyDropsArticleAndPunctuation() =
        assertEquals("simpsons hit run", TitleCleaner.sortKey("The Simpsons: Hit & Run"))
}
