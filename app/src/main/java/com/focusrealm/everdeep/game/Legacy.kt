package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class Talent(val id: String, val name: String, val desc: String, val maxRank: Int) {
    TORCH("torch", "Torchbearer", "begin each life with +1 potion per rank", 3),
    OWL("owl", "Owl-Eyed", "+1 sight radius per rank", 3),
    PURSE("purse", "Deep Pockets", "begin each life with +10 gold per rank", 3),
    SCHOLAR("scholar", "Scholar of the Deep", "+1 xp per kill per rank", 3),
    BULWARK("bulwark", "Bulwark", "+2 max hp per rank", 3),
    FORTUNE("fortune", "Fortune's Favor", "rarer finds on every floor per rank", 3);

    fun cost(nextRank: Int): Int = 2 + nextRank * 2
}

data class Vow(val id: String, val name: String, val desc: String,
               val goal: Int, val unit: String, val boon: String)

object Legacy {
    private val POOL = listOf(
        Vow("vigil", "Deep Vigil", "stay focused for 20 minutes", 20, "min", "RELIC"),
        Vow("watch", "Long Watch", "stay focused for 45 minutes", 45, "min", "HEAL_HP"),
        Vow("iron", "Iron Will", "leave a blocked app within 30s — 3 times", 3, "resist", "POTION"),
        Vow("oath", "Lineage Oath", "descend 3 floors while focused", 3, "floor", "XP"),
        Vow("purge", "The Purge", "the hero slays 40 monsters while you focus", 40, "kill", "ATK"),
        Vow("craft", "Sharpening", "the hero gains 2 levels while you focus", 2, "level", "RELIC"))

    fun rollFor(today: String): List<Vow> =
        POOL.shuffled(Random(today.hashCode())).take(3)

    fun boonName(boon: String): String = when (boon) {
        "RELIC" -> "a relic of the deep"
        "POTION" -> "two potions"
        "HEAL_HP" -> "an old blessing (+6 max hp, restored)"
        "XP" -> "a surge of insight (xp)"
        "ATK" -> "a whetstone edge (+1 atk)"
        else -> "a strange gift"
    }
}
