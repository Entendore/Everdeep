package com.focusrealm.everdeep.game

import kotlin.random.Random

/**
 * The hero's learning layer.
 *
 * Core: heuristic decision code reads tunable thresholds from here.
 * Learning 1 — stochastic hill-climbing: every TRIAL turns one tunable is
 *   nudged; the trial's accumulated reward (xp/gold/loot − damage − death)
 *   decides whether the nudge sticks or reverts.
 * Learning 2 — deathbed lessons: a death applies directed nudges to the
 *   parameters most responsible.
 *
 * Weights persist across heroes — the lineage remembers.
 */
class HeroBrain {

    class Tunable(val name: String, var value: Float,
                  val min: Float, val max: Float, val step: Float)

    val tunables = linkedMapOf<String, Tunable>(
        "aggression" to Tunable("aggression", 1.33f, 1.0f, 2.0f, 0.08f),
        "panic"      to Tunable("panic",      36f,    28f,   64f,   4f),
        "healGate"   to Tunable("healGate",   60f,    45f,   85f,   5f),
        "nukeSave"   to Tunable("nukeSave",   0.4f,   0f,    0.9f,  0.12f),
        "greed"      to Tunable("greed",      100f,   50f,   300f,  30f),
        "reserve"    to Tunable("reserve",    10f,    2f,    40f,   4f),
        "descent"    to Tunable("descent",    0.55f,  0.35f, 0.7f,  0.05f),
        "skirt"      to Tunable("skirt",      3f,     2f,    6f,    1f),
        "finish"     to Tunable("finish",     120f,   0f,    220f,  40f),
        "elite"      to Tunable("elite",      60f,    0f,    150f,  25f),
        "boss"       to Tunable("boss",       80f,    0f,    180f,  30f),
        "grit"       to Tunable("grit",       4f,     2f,    7f,    0.5f),
        "stairBias"  to Tunable("stairBias",  1.2f,   0.4f,  2.5f,  0.2f),
        "skillBias"  to Tunable("skillBias",  1f,     0.5f,  1.6f,  0.15f)
    )

    fun f(name: String): Float = tunables[name]!!.value

    var onLesson: ((String) -> Unit)? = null
    var onPersist: ((String) -> Unit)? = null
    var onReward: ((Float) -> Unit)? = null

    private var baseline = 0f
    private var hasBaseline = false
    private var trialParam: String? = null
    private var trialSign = 1
    private var trialReward = 0f
    private var trialStart = 0
    private var age = 0
    var lessons = 0
        private set

    companion object { private const val TRIAL = 50 }

    init { startTrial() }

    fun noteReward(r: Float) {
        if (trialParam != null) trialReward += r
        onReward?.invoke(r)
    }

    fun onTurn() {
        age++
        if (age - trialStart >= TRIAL) evaluate()
    }

    private fun startTrial() {
        val p = tunables.values.random()
        trialParam = p.name
        trialSign = if (Random.nextBoolean()) 1 else -1
        nudgeValue(p, p.step * trialSign)
        trialReward = 0f
        trialStart = age
    }

    private fun evaluate() {
        val p = tunables[trialParam] ?: run { startTrial(); return }
        val improved = !hasBaseline || trialReward > baseline + 0.5f
        if (improved) {
            if (hasBaseline) {
                lessons++
                onLesson?.invoke(Narrator.brainLesson(p.name, trialSign > 0))
            }
            baseline = if (hasBaseline) baseline * 0.6f + trialReward * 0.4f else trialReward
            hasBaseline = true
        } else {
            nudgeValue(p, -p.step * trialSign)   // revert
            baseline = baseline * 0.6f + trialReward * 0.4f
        }
        persist()
        startTrial()
    }

    private fun nudgeValue(t: Tunable, d: Float) {
        t.value = (t.value + d).coerceIn(t.min, t.max)
    }

    private fun bump(name: String, d: Float): Boolean {
        val t = tunables[name] ?: return false
        val before = t.value
        nudgeValue(t, d)
        return t.value != before
    }

    /** hard lessons drawn from a death; returns the parameter names that moved */
    fun deathLesson(byBoss: Boolean, potionsLeft: Int, adjacent: Int,
                    poisoned: Boolean): List<String> {
        val moved = ArrayList<String>()
        if (byBoss && bump("skirt", 2f)) moved.add("skirt")
        if (byBoss && bump("boss", 40f)) moved.add("boss")
        if (potionsLeft == 0 && bump("reserve", 6f)) moved.add("reserve")
        if (potionsLeft >= 2 && bump("panic", 6f)) moved.add("panic")
        if (adjacent >= 2 && bump("aggression", 0.15f)) moved.add("aggression")
        if (poisoned && bump("healGate", 8f)) moved.add("healGate")
        persist()
        return moved
    }

    /** adopt a genome's weights as this life's starting brain */
    fun absorb(values: Map<String, Float>) {
        for ((k, v) in values) tunables[k]?.let { it.value = v.coerceIn(it.min, it.max) }
        trialParam = null
        trialReward = 0f
        baseline = 0f
        hasBaseline = false
        startTrial()
    }

    fun serialize(): String =
        tunables.entries.joinToString(";") { "${it.key}=${it.value.value}" } +
        ";baseline=$baseline;hasBaseline=${if (hasBaseline) 1 else 0};lessons=$lessons"

    fun load(s: String) {
        for (pair in s.split(";")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size != 2) continue
            val v = kv[1].toFloatOrNull() ?: continue
            when (kv[0]) {
                "baseline" -> baseline = v
                "hasBaseline" -> hasBaseline = v > 0.5f
                "lessons" -> lessons = v.toInt()
                else -> tunables[kv[0]]?.let { it.value = v.coerceIn(it.min, it.max) }
            }
        }
        trialParam = null
        startTrial()
    }

    private fun persist() { onPersist?.invoke(serialize()) }
}
