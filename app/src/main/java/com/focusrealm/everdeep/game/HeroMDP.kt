package com.focusrealm.everdeep.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * The strategic layer as a Markov Decision Process, solved online with
 * tabular Q-learning:
 *
 *     Q(s,a) ← Q(s,a) + α · ( r + γ · max_a' Q(s',a') − Q(s,a) )
 *
 * State  — compact encoding of the hero's situation (hp, danger, supplies,
 *          floor knowledge, position, wealth, venom) → 4608 abstract states.
 * Action — one of eight strategic archetypes.
 * Reward — the same stream that trains the brain: xp/gold/loot − blood.
 *
 * Heuristic priors lead early; the learned values are trusted more as the
 * table fills (trust ramps with decisions) — the lineage literally learns
 * which choices pay in which situations.
 */
class HeroMDP {

    companion object {
        const val FIGHT = 0; const val WITHDRAW = 1; const val AID = 2
        const val LOOT = 3; const val NPC = 4; const val DESCEND = 5
        const val EXPLORE = 6; const val POTION = 7
        const val N_ACTIONS = 8
        private const val ALPHA = 0.12f    // learning rate
        private const val GAMMA = 0.92f    // discount — the deep rewards patience
    }

    private val q = HashMap<Long, FloatArray>()
    var decisions = 0
        private set
    var lastOverrode = false
        private set

    private val rewardAcc = ArrayDeque<Float>()

    fun size(): Int = q.size

    /** exploration decays with experience, never to zero */
    private fun epsilon(): Float = (0.25f - decisions / 12000f).coerceAtLeast(0.04f)

    /** how much the learned table outweighs heuristic priors */
    private fun trust(): Float = (decisions / 2500f).coerceAtMost(1f)

    /** rewards accumulate between decisions; consumed by the Bellman update */
    fun accrue(r: Float) {
        if (rewardAcc.size < 4) rewardAcc.add(r)
        else { rewardAcc.removeFirst(); rewardAcc.add(r) }
    }

    /** hpB(4) × danger(4) × potions(3) × knowledge(3) × stairs(2) × rich(2) × venom(2) */
    fun encode(hpPct: Int, dangerLvl: Int, potions: Int, explored: Float,
               onStairs: Boolean, rich: Boolean, poisoned: Boolean): Long {
        val hpB = when { hpPct <= 25 -> 0; hpPct <= 50 -> 1; hpPct <= 75 -> 2; else -> 3 }
        val pot = when { potions <= 0 -> 0; potions <= 2 -> 1; else -> 2 }
        val know = when { explored < 0.40f -> 0; explored < 0.70f -> 1; else -> 2 }
        var s = 0L
        s = s * 4 + hpB
        s = s * 4 + dangerLvl.coerceIn(0, 3)
        s = s * 3 + pot
        s = s * 3 + know
        s = s * 2 + if (onStairs) 1 else 0
        s = s * 2 + if (rich) 1 else 0
        s = s * 2 + if (poisoned) 1 else 0
        return s
    }

    private fun row(s: Long): FloatArray = q.getOrPut(s) { FloatArray(N_ACTIONS) }

    /** test seam */
    internal fun qRow(s: Long): FloatArray = row(s)

    /** Bellman update for the previous transition */
    fun learn(sPrev: Long, aPrev: Int, sNow: Long, availableNow: List<Int>) {
        var r = 0f
        while (rewardAcc.isNotEmpty()) r += rewardAcc.removeFirst()
        val qPrev = row(sPrev)
        val best = availableNow.maxOfOrNull { row(sNow)[it] } ?: 0f
        qPrev[aPrev] += ALPHA * (r + GAMMA * best - qPrev[aPrev])
    }

    /** ε-greedy over prior + trusted Q; reports whether learning flipped the instinct */
    fun choose(s: Long, candidates: List<Pair<Int, Float>>): Int {
        lastOverrode = false
        if (candidates.isEmpty()) return -1
        decisions++
        if (Random.nextFloat() < epsilon()) return candidates.random().first
        val qRow = row(s)
        var bestA = candidates[0].first
        var bestV = Float.NEGATIVE_INFINITY
        var priorBestA = candidates[0].first
        var bestPrior = Float.NEGATIVE_INFINITY
        for ((a, prior) in candidates) {
            val v = prior + trust() * qRow[a]
            if (v > bestV) { bestV = v; bestA = a }
            if (prior > bestPrior) { bestPrior = prior; priorBestA = a }
        }
        lastOverrode = decisions > 300 && bestA != priorBestA &&
                       (qRow[bestA] - qRow[priorBestA]) > 0.15f
        return bestA
    }

    fun serialize(): String = buildString {
        append("d=").append(decisions)
        for ((s, r) in q) {
            val nz = r.withIndex().filter { abs(it.value) > 0.01f }
            if (nz.isEmpty()) continue
            append(';').append(s).append(':')
            append(nz.joinToString(",") { "${it.index}=${it.value}" })
        }
    }

    fun load(s: String) {
        for (entry in s.split(";")) {
            if (entry.startsWith("d=")) {
                decisions = entry.substring(2).toIntOrNull() ?: 0; continue
            }
            val kv = entry.split(":", limit = 2)
            if (kv.size != 2) continue
            val key = kv[0].toLongOrNull() ?: continue
            val r = row(key)
            for (pair in kv[1].split(",")) {
                val p = pair.split("=")
                val i = p.getOrNull(0)?.toIntOrNull() ?: continue
                val v = p.getOrNull(1)?.toFloatOrNull() ?: continue
                if (i in 0 until N_ACTIONS) r[i] = v
            }
        }
    }
}
