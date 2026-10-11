package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class RivalPersona(val tag: String, val hostile: Boolean, val hint: String) {
    BLOODSWORN("Bloodsworn", true, "hunts your hero on sight"),
    GLORY("Glorybound", false, "fights everything that moves"),
    GREEDY("Coinminded", false, "wants the loot, not the war"),
    CAUTIOUS("Ghostwalker", false, "avoids the hero, races the stairs")
}

class Rival(val name: String, val glyph: Char, var x: Int, var y: Int, depth: Int) {
    val persona = RivalPersona.entries.random()
    val maxHp = 18 + depth * 5 + Random.nextInt(6)
    var hp = maxHp
    val atk = 3 + depth / 2 + Random.nextInt(3)
    val def = 1 + depth / 6
    var hoard = 10 + Random.nextInt(15)
    var carried: Item? = null
    var personalDepth = depth
    var hostile = persona.hostile
    var met = false
    var turnsHere = 0
    val alive get() = hp > 0
}

object RivalForge {
    val GLYPHS = listOf('Œ', 'Æ', 'Ł', 'Ŋ', 'Ź', 'Ž', 'Ť', 'Ř', 'Š', 'Ż')
    fun build(x: Int, y: Int, depth: Int): Rival =
        Rival(EliteForge.name(), GLYPHS.random(), x, y, depth)
}
