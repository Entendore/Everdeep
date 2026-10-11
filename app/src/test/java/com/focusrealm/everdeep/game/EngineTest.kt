package com.focusrealm.everdeep.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private class B : Engine.Bridge {
        var fallen = false
        override fun log(text: String, tone: Tone) {}
        override fun onDepth(depth: Int) {}
        override fun onLevelUp(level: Int) {}
        override fun onFallen(summary: RunSummary) { fallen = true }
        override fun onTurnEnd(hero: Hero, depth: Int, turns: Int) {}
        override fun onLesson(text: String) {}
        override fun onRivalEvent(name: String, glyph: Char, depth: Int, dead: Boolean) {}
    }

    private fun engine() = Engine().apply { bridge = B() }

    @Test
    fun stairsAreReachableFromStart_onManyFloors() {
        repeat(30) { i ->
            val e = engine()
            e.newRun("Mapper")
            val d = e.bfsDist(Pt(e.hero.x, e.hero.y))
            assertTrue("floor $i (depth ${e.depth})",
                d[e.key(e.stairs.x, e.stairs.y)] > 0)
        }
    }

    @Test
    fun aiSurvivesFiveHundredTurns() {
        val e = engine()
        e.newRun("Soak")
        val bridge = e.bridge as B
        var turns = 0
        while (turns < 500 && e.heroAlive) { e.heroTurn(); turns++ }
        assertTrue("died at turn $turns without onFallen",
            turns == 500 || bridge.fallen)
    }

    @Test
    fun runSnapshotRoundTrips() {
        val e = engine()
        e.newRun("Round")
        repeat(40) { if (e.heroAlive) e.heroTurn() }
        val snap = e.serializeRun()
        val e2 = engine()
        assertTrue(e2.restoreRun(snap))
        assertEquals(e.depth, e2.depth)
        assertEquals(e.hero.maxHp, e2.hero.maxHp)
        assertEquals(e.hero.gold, e2.hero.gold)
        assertEquals(e.hero.kills, e2.hero.kills)
        assertEquals(e.hero.skills.size, e2.hero.skills.size)
    }

    @Test
    fun staleOrUnversionedSnapshotsAreRejected() {
        val e = engine()
        assertFalse(e.restoreRun("1\u0000garbage\u0001data"))   // wrong schema
        assertFalse(e.restoreRun("no version marker at all"))   // no marker
        assertFalse(e.restoreRun(""))                            // empty
    }
}
