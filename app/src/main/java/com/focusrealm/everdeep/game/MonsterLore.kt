package com.focusrealm.everdeep.game

class MonsterLore {
    private val rep = HashMap<String, Float>()

    fun threat(id: String): Float = rep[id] ?: 1f

    fun noteFight(id: String, dmg: Int, expected: Int) {
        val surprise = when {
            dmg >= expected * 3 -> 1.45f
            dmg > expected      -> 1.15f
            dmg == 0            -> 0.80f
            else                -> 0.95f
        }
        val cur = rep[id] ?: 1f
        rep[id] = (cur * 0.8f + surprise * 0.2f).coerceIn(0.5f, 2.5f)
    }

    fun noteVictory(id: String) {
        rep[id] = ((rep[id] ?: 1f) * 0.95f).coerceAtLeast(0.5f)
    }

    fun serialize(): String = rep.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun load(s: String) {
        for (pair in s.split(";")) {
            val kv = pair.split("=", limit = 2)
            kv.getOrNull(1)?.toFloatOrNull()?.let { rep[kv[0]] = it.coerceIn(0.5f, 2.5f) }
        }
    }
}
