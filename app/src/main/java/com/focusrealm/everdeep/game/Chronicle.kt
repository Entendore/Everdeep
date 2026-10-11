package com.focusrealm.everdeep.game

import kotlin.random.Random

class ChronicleEvent(val turn: Int, val depth: Int, val tag: String, val text: String)

class Chronicle {
    val events = ArrayList<ChronicleEvent>()
    private var lastItem = ""
    var personaTitle: String? = null

    fun reset() { events.clear(); lastItem = "" }

    fun add(turn: Int, depth: Int, tag: String, text: String) {
        events.add(ChronicleEvent(turn, depth, tag, text))
        if (tag == "item") lastItem = text.substringBefore(" —")
        if (events.size > 400) events.removeAt(0)
    }

    fun count(tag: String) = events.count { it.tag == tag }

    private fun p(key: String, v: List<String>) = Narrator.pick(key, v)

    fun saga(name: String, s: RunSummary, bestStreak: Int, rivalsFelled: Int): List<String> {
        val out = ArrayList<String>()
        personaTitle?.let { out.add(p("sgPer", listOf(
            "She walked these stairs as $it.",
            "$it — that was her answer to the deep.",
            "History will file her under '$it'."))) }
        out.add(p("sgOpen", listOf(
            "$name went $s.depth floors down in ${s.turns} turns.",
            "${s.turns} turns of torchlight carried $name to floor $s.depth.",
            "Floor $s.depth is where the story of $name stops being written.")))
        out.add(p("sgKills", listOf(
            "${s.kills} things died behind her torch.",
            "${s.kills} graves dig themselves a little deeper when she passes.",
            "The deep counts ${s.kills} debts settled by her hand.")))
        if (bestStreak >= 5)
            out.add(p("sgStreak", listOf(
                "Her longest fury took $bestStreak without a pause for breath.",
                "$bestStreak fell in an unbroken string once — the floor still remembers.")))
        val wardens = count("warden")
        out.add(if (wardens > 0) p("sgWard", listOf(
            "The keepers of $wardens floors learned her name the hard way.",
            "$wardens wardens broken — the stairs stayed honest to her.")) else
            p("sgNoWard", listOf(
                "No keeper ever fell to her hand; she paid the stairs in silence.",
                "She passed the keepers like a rumor.")))
        if (rivalsFelled > 0)
            out.add(p("sgRiv", listOf(
                "She buried $rivalsFelled rival delvers and took their gold.",
                "$rivalsFelled torches competed with hers; $rivalsFelled went out.")))
        else if (count("rival") > 0)
            out.add("Rivals shared her stairs and lived to race her deeper.")
        if (lastItem.isNotEmpty())
            out.add(p("sgItem", listOf(
                "She carried wonders — $lastItem among them.",
                "$lastItem rode at her side to the end.")))
        val nd = count("nearDeath")
        if (nd > 0)
            out.add(p("sgND", listOf(
                "Death reached for her $nd times; each time the flask was faster.",
                "$nd times the lantern flickered to a coin-flip — and paid out.")))
        val sk = count("skill")
        if (sk > 0)
            out.add(p("sgSkill", listOf(
                "She learned the deep's arts one by one — $sk in all.",
                "$sk arts written into her hands by fire and failure.")))
        if (count("npc") > 0)
            out.add(p("sgNpc", listOf(
                "She met the strange folk of the deep and left most of them richer.",
                "The torch-bearers and dice-rollers will tell stories about her.")))
        out.add(p("sgClose", listOf(
            "The deep keeps the rest.",
            "What remains, the stairs hold.",
            "The rest is silence, and gold, and bone.")))
        return out
    }
}
