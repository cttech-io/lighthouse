package io.cttech.lighthouse.data

import androidx.compose.ui.graphics.Color
import io.cttech.lighthouse.model.Game
import io.cttech.lighthouse.model.Platform

/** Hard-coded library for milestone 1 (UI only). Replaced by the real library engine in milestone 2. */
object SampleLibrary {
    val platforms = listOf(
        Platform("android", "Android", Color(0xFF3DDC84)),
        Platform("switch", "Switch", Color(0xFFFF4554)),
        Platform("ps2", "PS2", Color(0xFF2F6BFF)),
        Platform("gba", "GBA", Color(0xFF7A5CFF)),
    )

    val games = listOf(
        Game("moonlight", "android", "Moonlight", subtitle = "Game streaming"),
        Game("steamlink", "android", "Steam Link", subtitle = "Game streaming"),
        Game("droiddeck", "android", "DroidDeck", subtitle = "Steam on Android"),
        Game("silksong", "android", "Hollow Knight: Silksong", 2025),
        Game("eden", "android", "Eden", subtitle = "Switch emulator"),
        Game("armsx2", "android", "ARMSX2", subtitle = "PS2 emulator"),
        Game("retroarch", "android", "RetroArch", subtitle = "Multi-system emulator"),
        Game("tailscale", "android", "Tailscale", subtitle = "Program"),
        Game("botw", "switch", "The Legend of Zelda: Breath of the Wild", 2017),
        Game("totk", "switch", "The Legend of Zelda: Tears of the Kingdom", 2023),
        Game("odyssey", "switch", "Super Mario Odyssey", 2017),
        Game("dread", "switch", "Metroid Dread", 2021),
        Game("gt3", "ps2", "Gran Turismo 3: A-Spec", 2001),
        Game("gtasa", "ps2", "Grand Theft Auto: San Andreas", 2004),
        Game("hitrun", "ps2", "The Simpsons: Hit & Run", 2003),
        Game("ratchet", "ps2", "Ratchet & Clank", 2002),
        Game("sotc", "ps2", "Shadow of the Colossus", 2005),
        Game("emerald", "gba", "Pokémon Emerald", 2004),
        Game("firered", "gba", "Pokémon FireRed", 2004),
        Game("pmd", "gba", "Pokémon Mystery Dungeon: Red Rescue Team", 2005),
        Game("minish", "gba", "The Legend of Zelda: The Minish Cap", 2004),
        Game("advancewars", "gba", "Advance Wars", 2001),
        Game("metroidfusion", "gba", "Metroid Fusion", 2002),
        Game("mariokart", "gba", "Mario Kart: Super Circuit", 2001),
    )

    val initialFavourites = linkedSetOf("armsx2", "droiddeck", "silksong", "moonlight", "steamlink", "botw")
}
