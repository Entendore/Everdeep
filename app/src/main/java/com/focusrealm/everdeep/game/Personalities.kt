package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class Temperament(
    val tag: String, val epithet: String,
    val add: Map<String, Float> = emptyMap()
) {
    BOLD("Bold", "caution is for those with something to lose",
        mapOf("aggression" to -0.15f, "panic" to -6f, "boss" to -25f, "skirt" to -0.5f,
              "grit" to 1f)),
    TIMID("Timid", "the deep respects a careful step",
        mapOf("aggression" to 0.2f, "panic" to 8f, "skirt" to 1f, "reserve" to 6f,
              "grit" to -1f)),
    RECKLESS("Reckless", "spend everything now; the stair is a problem for later",
        mapOf("aggression" to -0.25f, "panic" to -10f, "nukeSave" to -0.25f,
              "healGate" to -8f, "grit" to 1.5f)),
    PATIENT("Patient", "every floor is read twice before it is left",
        mapOf("descent" to 0.06f, "greed" to 40f, "healGate" to 5f)),
    RESTLESS("Restless", "the stairs call; the rest is scenery",
        mapOf("descent" to -0.07f, "greed" to -30f, "finish" to 20f)),
    STOIC("Stoic", "pain is a rumor the flask need not answer",
        mapOf("panic" to -8f, "healGate" to 12f, "reserve" to 4f)),
    VORACIOUS("Voracious", "gold first, blood second, stairs last",
        mapOf("greed" to 90f, "reserve" to 8f, "descent" to 0.04f)),
    DUELIST("Duellist", "a strong arm is its own map",
        mapOf("finish" to 40f, "elite" to 30f, "aggression" to -0.1f, "panic" to -4f))
}

enum class Focus(val tag: String, val epithet: String,
                 val fightMul: Float, val lootMul: Float,
                 val exploreMul: Float, val huntsRivals: Boolean) {
    BLADE("Blade", "the sword is the argument", 0.80f, 1.00f, 1.05f, false),
    COIN("Coin", "fortune favors the counted purse", 1.15f, 1.90f, 1.00f, false),
    DEPTHS("Depths", "the bottom is a promise", 1.05f, 0.60f, 0.70f, false),
    LORE("Lore", "the deep is a book; read it", 1.10f, 1.15f, 1.25f, false),
    HUNTER("Hunter", "rivals are the only fair fight", 0.90f, 1.05f, 1.00f, true),
    WARDEN("Warden", "survive first; everything else is gossip", 1.25f, 1.10f, 1.10f, false)
}

class Personality(val temperament: Temperament, val focus: Focus) {
    val title: String get() = "the ${temperament.tag} ${focus.tag}"

    fun adjusted(name: String, learned: Float): Float {
        var v = learned + (temperament.add[name] ?: 0f)
        return when (name) {
            "panic"      -> v.coerceAtLeast(20f)
            "descent"    -> v.coerceIn(0.30f, 0.75f)
            "aggression" -> v.coerceIn(0.75f, 2.2f)
            else -> v
        }
    }
}

object PersonalityForge {
    fun forge(scores: Map<String, Float>, epsilon: Int = 35): Personality {
        val temps = Temperament.entries
        val temp = if (Random.nextInt(100) < epsilon) temps.random()
        else temps[weighted(temps.map { 1f + (scores[it.name] ?: 3f) })]
        return Personality(temp, Focus.entries.random())
    }

    private fun weighted(weights: List<Float>): Int {
        var sum = 0f; for (w in weights) sum += w
        var r = Random.nextFloat() * sum
        for (i in weights.indices) { r -= weights[i]; if (r <= 0f) return i }
        return weights.lastIndex
    }
}
