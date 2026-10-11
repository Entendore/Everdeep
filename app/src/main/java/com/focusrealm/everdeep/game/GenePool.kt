package com.focusrealm.everdeep.game

import kotlin.random.Random

/** One hereditary line: the brain's starting weights for a life. */
class Genome(val id: Int, val values: MutableMap<String, Float>,
             var fitness: Float = 0f, var origin: String = "root") {
    fun describe(): String = "G$id"
}

/**
 * A genetic algorithm over hero brains.
 *
 * Each life is a fitness evaluation (depth, kills, level, gold). On death
 * the genome — carrying its lifetime-learned refinements, Lamarckian-style —
 * re-enters the pool, the weakest line is culled, and the next hero is bred:
 *
 *   20% elitism    — the champion's genome, lightly mutated
 *   60% crossover  — uniform crossover of two tournament winners + mutation
 *   20% immigrant  — fresh random blood, for diversity
 *
 * Selection pressure comes from the fitness scores; the pool keeps the
 * strongest POOL_SIZE lines across the whole lineage.
 */
class GenePool(private val bounds: Map<String, HeroBrain.Tunable>) {

    companion object {
        const val POOL_SIZE = 8
        fun defaultBounds(): Map<String, HeroBrain.Tunable> = HeroBrain().tunables
    }

    val population = ArrayList<Genome>()
    private var nextId = 1
    var generation = 0
        private set

    private fun randomValue(name: String): Float {
        val t = bounds[name] ?: return 0f
        return t.min + Random.nextFloat() * (t.max - t.min)
    }

    private fun mutate(g: Genome, chance: Float = 0.25f, strength: Float = 0.12f) {
        for ((name, t) in bounds) {
            if (Random.nextFloat() > chance) continue
            val cur = g.values[name] ?: randomValue(name)
            val span = (t.max - t.min) * strength
            g.values[name] = (cur + Random.nextFloat() * 2f * span - span)
                .coerceIn(t.min, t.max)
        }
    }

    private fun tournament(k: Int = 3): Genome {
        var best = population.random()
        repeat(k - 1) {
            val c = population.random()
            if (c.fitness > best.fitness) best = c
        }
        return best
    }

    private fun crossover(a: Genome, b: Genome): Genome {
        val child = Genome(nextId++, mutableMapOf(), origin = "cross")
        for (name in bounds.keys) {
            val src = if (Random.nextBoolean()) a else b
            child.values[name] = src.values[name] ?: randomValue(name)
        }
        return child
    }

    /** spawn the genome for the next hero */
    fun spawn(): Genome {
        generation++
        if (population.size < POOL_SIZE / 2) {   // still seeding the pool
            return if (population.isEmpty() || Random.nextBoolean()) {
                val g = Genome(nextId++, mutableMapOf(), origin = "root")
                for (name in bounds.keys) g.values[name] = randomValue(name)
                g
            } else {
                val g = Genome(nextId++, mutableMapOf(), origin = "clone")
                champion().values.forEach { (k, v) -> g.values[k] = v }
                mutate(g)
                g
            }
        }
        return when (Random.nextInt(100)) {
            in 0 until 20 -> {   // elitism: the champion, lightly varied
                val heir = Genome(nextId++, champion().values.toMutableMap(), origin = "heir")
                mutate(heir, 0.15f, 0.05f)
                heir
            }
            in 20 until 80 ->   // crossover of two tournament winners + mutation
                crossover(tournament(), tournament()).also { mutate(it) }
            else -> {            // immigrant: fresh blood
                val g = Genome(nextId++, mutableMapOf(), origin = "immigrant")
                for (name in bounds.keys) g.values[name] = randomValue(name)
                g
            }
        }
    }

    private fun champion(): Genome = population.maxByOrNull { it.fitness }!!

    /** a life ends: score it, fold it back in, cull the weakest */
    fun submit(g: Genome, fitness: Float) {
        g.fitness = fitness
        population.removeAll { it.id == g.id }
        population.add(g)
        population.sortByDescending { it.fitness }
        while (population.size > POOL_SIZE) population.removeAt(population.size - 1)
    }

    fun serialize(): String = buildString {
        append("gen=").append(generation).append("|next=").append(nextId)
        for (g in population) {
            append('|').append(g.id).append(',').append(g.fitness).append(',')
            append(g.origin).append(',')
            append(g.values.entries.joinToString("~") { "${it.key}=${it.value}" })
        }
    }

    fun load(s: String) {
        population.clear()
        for (part in s.split("|")) {
            when {
                part.startsWith("gen=") ->
                    generation = part.substring(4).toIntOrNull() ?: 0
                part.startsWith("next=") ->
                    nextId = part.substring(4).toIntOrNull() ?: 1
                part.contains(',') -> {
                    val seg = part.split(",", limit = 3)
                    val id = seg.getOrNull(0)?.toIntOrNull() ?: continue
                    val fit = seg.getOrNull(1)?.toFloatOrNull() ?: 0f
                    val rest = seg.getOrNull(2) ?: continue
                    val g = Genome(id, mutableMapOf(), fitness = fit,
                                   origin = rest.substringBefore(","))
                    for (pair in rest.substringAfter(",", "").split("~")) {
                        val kv = pair.split("=", limit = 2)
                        kv.getOrNull(1)?.toFloatOrNull()?.let { g.values[kv[0]] = it }
                    }
                    population.add(g)
                }
            }
        }
        population.sortByDescending { it.fitness }
    }
}
