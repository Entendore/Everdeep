package com.focusrealm.everdeep.game

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Monte Carlo Tree Search over the hero's next few steps.
 *
 * Strategy (the MDP) picks the intent; this planner executes it with
 * adversarial lookahead. Each iteration: select a line through the tree with
 * UCB1, step the hero (moving onto a monster = attacking), let the monsters
 * respond, finish with a heuristic rollout, then backpropagate. The best
 * *visited* root action is returned.
 *
 * The simulated world is a light snapshot — positions, hp, threat — plus
 * lambdas into the real map. The engine's state is never touched during
 * planning, so search is side-effect free and cheap.
 */
class TactMCTS {

    companion object {
        const val WAIT = 0
        private const val NI = 1; private const val SI = 2
        private const val WI = 3; private const val EI = 4
        private const val UCB_C = 1.25f
        private const val DEATH = -120f
        private val DX = intArrayOf(0, 0, 0, -1, 1)
        private val DY = intArrayOf(0, -1, 1, 0, 0)
    }

    enum class Goal { HUNT, FLEE }

    /** one nearby monster, simplified for search */
    class Foe(var x: Int, var y: Int, var hp: Int, val atk: Int, val threat: Int,
              val flees: Boolean, val fast: Boolean)

    /** a light snapshot of the tactical situation */
    class Plan(
        val heroX: Int, val heroY: Int, val heroHp: Int,
        val heroAtk: Int, val heroDef: Int,
        val heroThorns: Int, val critChance: Int,
        val foes: List<Foe>,
        val loot: List<Triple<Int, Int, Int>>,          // x, y, value
        val goal: Goal,
        val stairsX: Int, val stairsY: Int,
        val stairPull: Float,                            // FLEE drift toward the exit
        val passable: (Int, Int) -> Boolean,             // hero legality (traps, walls)
        val walkable: (Int, Int) -> Boolean              // monster legality (walls)
    )

    private class Node(val parent: Node? = null) {
        var visits = 0
        var value = 0f
        val children = arrayOfNulls<Node>(5)
    }

    private class Sim(
        var hx: Int, var hy: Int, var hp: Int,
        val foes: ArrayList<Foe>,
        val loot: ArrayList<Triple<Int, Int, Int>>
    ) {
        fun clone(): Sim {
            val f = ArrayList<Foe>(foes.size)
            for (o in foes) f.add(Foe(o.x, o.y, o.hp, o.atk, o.threat, o.flees, o.fast))
            return Sim(hx, hy, hp, f, ArrayList(loot))
        }
    }

    var plans = 0
        private set
    var overrides = 0

    // ---------- the search ----------

    fun search(p: Plan, iterations: Int, depthMax: Int = 7): Int {
        plans++
        val root = Node()
        val start = Sim(p.heroX, p.heroY, p.heroHp, ArrayList(p.foes), ArrayList(p.loot))
        for (i in 0 until iterations) {
            val s = start.clone()
            var node = root
            var depth = 0
            var total = 0f
            while (depth < depthMax) {
                val a = pick(node, s, p)
                if (a < 0) break
                val r = step(s, a, p)
                if (r <= -1000f) break
                total += r
                var child = node.children[a]
                if (child == null) { child = Node(node); node.children[a] = child }
                node = child
                depth++
            }
            total += rollout(s, p, depth, depthMax)
            var n: Node? = node
            while (n != null) { n.visits++; n.value += total; n = n.parent }
        }
        var bestA = -1; var bestN = 0
        for (a in 1..4) {
            val c = root.children[a] ?: continue
            if (c.visits > bestN) { bestN = c.visits; bestA = a }
        }
        return bestA    // -1 or WAIT → "no better line found"; engine falls back
    }

    // ---------- selection (UCB1) ----------

    private fun pick(node: Node, s: Sim, p: Plan): Int {
        var unvisited = -1
        var bestA = -1
        var bestV = Float.NEGATIVE_INFINITY
        val lnN = ln(node.visits.coerceAtLeast(2).toFloat())
        for (a in 0..4) {
            if (a != WAIT) {
                val nx = s.hx + DX[a]; val ny = s.hy + DY[a]
                val foe = s.foes.firstOrNull { it.x == nx && it.y == ny }
                if (foe == null && !p.passable(nx, ny)) continue
            }
            val c = node.children[a]
            if (c == null || c.visits == 0) {
                if (unvisited < 0) unvisited = a
                continue
            }
            val v = c.value / c.visits + UCB_C * sqrt(lnN / c.visits)
            if (v > bestV) { bestV = v; bestA = a }
        }
        return if (unvisited >= 0) unvisited else bestA
    }

    // ---------- one simulated ply: hero acts, monsters respond ----------

    private fun step(s: Sim, a: Int, p: Plan): Float {
        var r = 0f
        if (a != WAIT) {
            val nx = s.hx + DX[a]; val ny = s.hy + DY[a]
            val f = s.foes.firstOrNull { it.x == nx && it.y == ny }
            if (f != null) {                                   // move onto a foe = strike
                var dmg = maxOf(1, p.heroAtk + Random.nextInt(-1, 2))
                if (Random.nextInt(100) < p.critChance) dmg *= 2
                f.hp -= dmg
                r += dmg * (0.9f + f.threat / 40f)
                if (f.hp <= 0) { s.foes.remove(f); r += 12f + f.threat / 4f }
            } else {
                if (!p.passable(nx, ny)) return -1000f
                s.hx = nx; s.hy = ny
                val li = s.loot.indexOfFirst { it.first == nx && it.second == ny }
                if (li >= 0) { r += s.loot[li].third; s.loot.removeAt(li) }
            }
        }
        for (f in s.foes.toList()) {
            val dx = s.hx - f.x; val dy = s.hy - f.y
            if (abs(dx) + abs(dy) == 1) {                      // it bites
                val dmg = maxOf(1, f.atk + Random.nextInt(-1, 2) - p.heroDef)
                s.hp -= dmg
                r -= dmg * 2.2f
                if (p.heroThorns > 0) {
                    f.hp -= p.heroThorns
                    if (f.hp <= 0) { s.foes.remove(f); r += 10f }
                }
                if (s.hp <= 0) return r + DEATH
            } else if (Random.nextFloat() < 0.85f) {           // it closes in
                moveFoe(s, f, p)
                if (f.fast && Random.nextInt(100) < 40) {
                    val dx2 = s.hx - f.x; val dy2 = s.hy - f.y
                    if (abs(dx2) + abs(dy2) == 1) {
                        val dmg = maxOf(1, f.atk - p.heroDef)
                        s.hp -= dmg; r -= dmg * 2.2f
                        if (s.hp <= 0) return r + DEATH
                    } else moveFoe(s, f, p)
                }
            }
        }
        return r
    }

    private fun moveFoe(s: Sim, f: Foe, p: Plan) {
        val dx = s.hx - f.x; val dy = s.hy - f.y
        val acts = stepActions(if (f.flees) -dx else dx, if (f.flees) -dy else dy)
        for (a in acts) {
            if (a == WAIT) continue
            val nx = f.x + DX[a]; val ny = f.y + DY[a]
            if (nx == s.hx && ny == s.hy) return
            if (s.foes.any { it !== f && it.x == nx && it.y == ny }) continue
            if (!p.walkable(nx, ny)) continue
            f.x = nx; f.y = ny
            return
        }
    }

    // ---------- rollout & evaluation ----------

    private fun rollout(s: Sim, p: Plan, depth: Int, depthMax: Int): Float {
        var v = 0f
        var d = depth
        while (d < depthMax && s.hp > 0 && s.foes.isNotEmpty()) {
            val a = greedy(s, p)
            if (a < 0) break
            val r = step(s, a, p)
            if (r <= -1000f) break
            v += r
            d++
        }
        return v + evaluate(s, p)
    }

    private fun greedy(s: Sim, p: Plan): Int {
        var nearest: Foe? = null; var nd = Int.MAX_VALUE
        for (f in s.foes) {
            val d = abs(f.x - s.hx) + abs(f.y - s.hy)
            if (d < nd) { nd = d; nearest = f }
        }
        val f = nearest ?: return -1
        if (p.goal == Goal.FLEE) {
            if (nd <= 2) {
                var bestA = WAIT; var bestD = nd
                for (a in 1..4) {
                    val nx = s.hx + DX[a]; val ny = s.hy + DY[a]
                    if (s.foes.any { it.x == nx && it.y == ny }) continue
                    if (!p.passable(nx, ny)) continue
                    val dmin = s.foes.minOf { abs(it.x - nx) + abs(it.y - ny) }
                    if (dmin > bestD) { bestD = dmin; bestA = a }
                }
                return bestA
            }
            return WAIT
        }
        return heroToward(s, p, f.x, f.y)
    }

    private fun heroToward(s: Sim, p: Plan, tx: Int, ty: Int): Int {
        for (a in stepActions(tx - s.hx, ty - s.hy)) {
            if (a == WAIT) continue
            val nx = s.hx + DX[a]; val ny = s.hy + DY[a]
            if (s.foes.any { it.x == nx && it.y == ny }) return a
            if (p.passable(nx, ny)) return a
        }
        return WAIT
    }

    private fun evaluate(s: Sim, p: Plan): Float {
        var v = 0f
        if (s.hp <= 0) v += DEATH
        if (s.foes.isEmpty()) { v += 18f }
        else {
            var dmin = Int.MAX_VALUE
            for (f in s.foes) dmin = minOf(dmin, abs(f.x - s.hx) + abs(f.y - s.hy))
            when (p.goal) {
                Goal.HUNT -> v -= dmin * 0.4f
                Goal.FLEE -> {
                    v += dmin * 1.5f
                    v -= (abs(p.stairsX - s.hx) + abs(p.stairsY - s.hy)) * p.stairPull
                }
            }
        }
        return v
    }

    private fun stepActions(dx: Int, dy: Int): IntArray {
        val ax = if (dx > 0) EI else if (dx < 0) WI else WAIT
        val ay = if (dy > 0) SI else if (dy < 0) NI else WAIT
        return if (abs(dx) >= abs(dy)) intArrayOf(ax, ay) else intArrayOf(ay, ax)
    }
}
