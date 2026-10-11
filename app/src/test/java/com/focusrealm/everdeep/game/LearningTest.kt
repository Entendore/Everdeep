package com.focusrealm.everdeep.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearningTest {

    @Test
    fun brainSerializesAndLoads() {
        val a = HeroBrain()
        a.tunables["panic"]!!.value = 50f
        val b = HeroBrain().apply { load(a.serialize()) }
        assertEquals(50f, b.tunables["panic"]!!.value, 0.001f)
        assertEquals(a.lessons, b.lessons)
    }

    @Test
    fun genePoolStaysBoundedAndSorted() {
        val pool = GenePool(HeroBrain().tunables)
        repeat(20) { pool.submit(pool.spawn(), Random.nextFloat() * 100f) }
        assertTrue(pool.population.size <= GenePool.POOL_SIZE)
        assertEquals(pool.population.maxOf { it.fitness },
                     pool.population.first().fitness, 0.001f)
    }

    @Test
    fun genePoolRoundTripsGeneration() {
        val pool = GenePool(HeroBrain().tunables)
        repeat(6) { pool.submit(pool.spawn(), Random.nextFloat() * 50f) }
        val p2 = GenePool(HeroBrain().tunables)
        p2.load(pool.serialize())
        assertEquals(pool.generation, p2.generation)
        assertEquals(pool.population.size, p2.population.size)
        assertEquals(pool.population.first().id, p2.population.first().id)
    }

    @Test
    fun mdpEncodeIsDeterministic_andLearningMovesQ() {
        val m = HeroMDP()
        val s = m.encode(50, 1, 1, 0.5f, false, false, false)
        assertEquals(s, m.encode(50, 1, 1, 0.5f, false, false, false))
        val before = m.qRow(s)[HeroMDP.FIGHT]
        m.accrue(1f)
        m.learn(s, HeroMDP.FIGHT, s, listOf(HeroMDP.FIGHT))
        assertTrue(m.qRow(s)[HeroMDP.FIGHT] > before)
    }

    @Test
    fun mctsReturnsLegalActionOrNoOp() {
        val foe = TactMCTS.Foe(5, 6, 10, 3, 10, false, false)
        val ok = { x: Int, y: Int -> x in 0..12 && y in 0..12 }
        val plan = TactMCTS.Plan(5, 5, 30, 6, 1, 0, 10, listOf(foe), emptyList(),
            TactMCTS.Goal.HUNT, 20, 20, 0.1f, ok, ok)
        val a = TactMCTS().search(plan, 60)
        assertTrue("got $a", a == -1 || a in 1..4)
    }
}
