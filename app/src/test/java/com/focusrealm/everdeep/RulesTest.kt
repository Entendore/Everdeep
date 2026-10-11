package com.focusrealm.everdeep

import com.focusrealm.everdeep.game.Focus
import com.focusrealm.everdeep.game.MonsterLore
import com.focusrealm.everdeep.game.Personality
import com.focusrealm.everdeep.game.Temperament
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun siteMatchingHandlesSubdomains_safely() {
        FocusBus.siteBlocklist.value = setOf("reddit.com")
        assertTrue(FocusBus.siteMatches("reddit.com"))
        assertTrue(FocusBus.siteMatches("old.reddit.com"))
        assertFalse(FocusBus.siteMatches("notreddit.com"))
        assertFalse(FocusBus.siteMatches("reddit.com.evil.io"))
    }

    @Test
    fun personalityClampsLearnedValues() {
        val p = Personality(Temperament.TIMID, Focus.WARDEN)
        assertEquals(20f, p.adjusted("panic", 10f), 0.001f)
    }

    @Test
    fun bestiaryFearStaysBounded() {
        val l = MonsterLore()
        repeat(60) { l.noteFight("rat", 99, 2) }
        assertTrue(l.threat("rat") <= 2.5f)
        repeat(60) { l.noteVictory("rat") }
        assertTrue(l.threat("rat") >= 0.5f)
    }
}
