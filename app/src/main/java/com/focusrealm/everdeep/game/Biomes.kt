package com.focusrealm.everdeep.game

enum class Gen { BSP, CAVES, MINES, RUINS }

data class Cluster(val glyph: Char, val count: Int, val size: Int, val color: Int)

data class Biome(
    val id: String, val name: String, val icon: String, val tagline: String,
    val gen: Gen, val pool: List<MType>, val wardenName: String,
    val wallLit: Int, val wallDim: Int, val floorLit: Int, val floorDim: Int,
    val accent: Int,
    val decor: List<Triple<Char, Float, Int>> = emptyList(),
    val wallDecor: List<Triple<Char, Float, Int>> = emptyList(),
    val clusters: List<Cluster> = emptyList(),
    val school: School = School.BLADE
)

object Biomes {

    val CRYPT = Biome(
        id = "crypt", name = "The Ashen Crypts", icon = "⚰️",
        tagline = "cold stone and older silence",
        gen = Gen.BSP, pool = Bestiary.CRYPT, wardenName = "Crypt Warden",
        wallLit = 0xFF3A3228.toInt(), wallDim = 0xFF221D16.toInt(),
        floorLit = 0xFF1A1611.toInt(), floorDim = 0xFF131009.toInt(),
        accent = 0xFFC9C2B0.toInt(), school = School.BLADE,
        decor = listOf(Triple(',', 0.05f, 0xFF8D8168.toInt()),
                       Triple('"', 0.02f, 0xFF6B6150.toInt())),
        wallDecor = listOf(Triple('‡', 0.05f, 0xFFD98E4A.toInt())))

    val FUNGAL = Biome(
        id = "fungal", name = "The Fungal Hollows", icon = "🍄",
        tagline = "the air glows faintly green",
        gen = Gen.CAVES, pool = Bestiary.FUNGAL, wardenName = "Spore Tyrant",
        wallLit = 0xFF2C4230.toInt(), wallDim = 0xFF18241A.toInt(),
        floorLit = 0xFF16211B.toInt(), floorDim = 0xFF101812.toInt(),
        accent = 0xFF9FD08A.toInt(), school = School.BLOOM,
        decor = listOf(Triple(',', 0.03f, 0xFF6F8F5A.toInt())),
        wallDecor = listOf(Triple('"', 0.05f, 0xFF5A7A4A.toInt())),
        clusters = listOf(Cluster('♠', 5, 8, 0xFFD98FB0.toInt())))

    val EMBER = Biome(
        id = "ember", name = "The Ember Mines", icon = "⚒️",
        tagline = "heat breathes through the seams",
        gen = Gen.MINES, pool = Bestiary.EMBER, wardenName = "Forge Overseer",
        wallLit = 0xFF3E2A20.toInt(), wallDim = 0xFF241711.toInt(),
        floorLit = 0xFF1E130D.toInt(), floorDim = 0xFF150D09.toInt(),
        accent = 0xFFF0A35C.toInt(), school = School.EMBER,
        decor = listOf(Triple('·', 0.06f, 0xFFE2703A.toInt())),
        wallDecor = listOf(Triple('^', 0.04f, 0xFF8A4A2A.toInt())),
        clusters = listOf(Cluster('~', 4, 7, 0xFFD9542E.toInt())))

    val DROWNED = Biome(
        id = "drowned", name = "The Drowned Halls", icon = "🌊",
        tagline = "black water laps at the walls",
        gen = Gen.CAVES, pool = Bestiary.DROWNED, wardenName = "Tide Warden",
        wallLit = 0xFF22303A.toInt(), wallDim = 0xFF131C23.toInt(),
        floorLit = 0xFF101A21.toInt(), floorDim = 0xFF0B1217.toInt(),
        accent = 0xFF7FB4C9.toInt(), school = School.TIDE,
        decor = listOf(Triple('"', 0.03f, 0xFF4E7E96.toInt())),
        clusters = listOf(Cluster('≈', 6, 9, 0xFF4E7E96.toInt())))

    val VOIDREACH = Biome(
        id = "void", name = "The Voidreach", icon = "🌑",
        tagline = "the stars here are wrong",
        gen = Gen.RUINS, pool = Bestiary.VOIDREACH, wardenName = "Null Sentinel",
        wallLit = 0xFF2B2438.toInt(), wallDim = 0xFF181424.toInt(),
        floorLit = 0xFF141020.toInt(), floorDim = 0xFF0E0B16.toInt(),
        accent = 0xFFB39DDB.toInt(), school = School.VOID,
        decor = listOf(Triple('*', 0.04f, 0xFFB39DDB.toInt()),
                       Triple('^', 0.05f, 0xFF4A4060.toInt())),
        wallDecor = listOf(Triple('^', 0.04f, 0xFF3A3250.toInt())))

    val ALL = listOf(CRYPT, FUNGAL, EMBER, DROWNED, VOIDREACH)
    fun forDepth(depth: Int): Biome = ALL[((depth - 1) / 3).mod(ALL.size)]
}
