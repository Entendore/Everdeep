package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class Quirk(val tag: String, val hint: String) {
    COWARD("Craven", "flees when hurt"),
    BERSERK("Enraged", "fights harder when hurt"),
    AMBUSHER("Ambusher", "strikes from hiding once"),
    VENOMOUS("Venomous", "its bites fester"),
    THIEF("Light-fingered", "steals coin with every strike"),
    PACK("Pack Hunter", "calls its kin"),
    UNDYING("Undying", "refuses the first death"),
    REGEN("Roiling", "knits its own wounds"),
    SENTINEL("Sentinel", "watches far"),
    HOARDER("Hoarder", "carries more than it should")
}

enum class Affix(val tag: String) {
    VICIOUS("Vicious"), IRONHIDE("Iron-hide"), COLOSSAL("Colossal"),
    SWIFT("Swift"), ANCIENT("Ancient")
}

enum class NpcQuirk(val tag: String) {
    NONE("—"), HAGGLER("Haggler"), PATRON("Patron"), VINDICTIVE("Vindictive"),
    SWIFT("Unmoving"), DOOMSAYER("Doomsayer"), FORGETFUL("Forgetful")
}

object EliteForge {
    private val EPITHET = listOf(
        "the Sallow", "the Unrepentant", "of the Long Dark", "the Twice-Buried",
        "Who Remembers", "the Wound That Walks", "the Patient", "of Many Names",
        "the Lantern-Eater", "Who Counts the Stairs", "the Uninvited", "the Cold-Willed",
        "Torch-Breaker", "the Last Witness", "of the Broken Vow", "the Hollow-Eyed")
    fun name(): String = "${NpcForge.name()} ${EPITHET.random()}"
}
