package com.focusrealm.everdeep.game

class Feat(val id: String, val name: String, val desc: String, val embers: Int)

object Feats {
    val ALL = listOf(
        Feat("blood", "First Blood", "slay your first monster", 1),
        Feat("century", "The Century", "100 monsters slain across the lineage", 3),
        Feat("five", "Below the Light", "reach floor 5", 2),
        Feat("ten", "Halfway to Never", "reach floor 10", 4),
        Feat("fifteen", "The Cycle Turns", "reach floor 15 — back to the crypts", 6),
        Feat("warden", "Keeper-Breaker", "break a floor's warden", 3),
        Feat("rival", "Torch Snuffed", "fell a rival delver", 2),
        Feat("legend", "Legend-Bearer", "equip a legendary item", 3),
        Feat("scholar", "The Patient Ledger", "the MDP reaches 500 decisions", 3),
        Feat("gen10", "Ten Generations", "the gene pool reaches generation 10", 2))
}
