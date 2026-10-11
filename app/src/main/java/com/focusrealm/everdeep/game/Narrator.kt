package com.focusrealm.everdeep.game

import kotlin.random.Random

/**
 * Procedural narration for the Everdeep.
 * Every line the game tells comes from a template bank with anti-repetition,
 * biome-flavoured vocab, and generated monster epithets.
 */
object Narrator {

    // ---------- picker with anti-repetition ----------

    private val lastPick = HashMap<String, Int>()

    fun pick(key: String, variants: List<String>): String {
        if (variants.isEmpty()) return ""
        if (variants.size == 1) return variants[0]
        val last = lastPick[key] ?: -1
        var i = Random.nextInt(variants.size)
        if (i == last) i = (i + 1 + Random.nextInt(variants.size - 1)) % variants.size
        lastPick[key] = i
        return variants[i]
    }

    fun fill(tpl: String, vararg args: Any?): String {
        var out = tpl
        args.forEachIndexed { i, a -> out = out.replace("{$i}", a.toString()) }
        return out
    }

    private fun line(key: String, variants: List<String>, vararg args: Any?): String =
        fill(pick(key, variants), *args)

    fun roman(n: Int): String {
        var v = n; val sb = StringBuilder()
        for ((num, sym) in listOf(100 to "C", 50 to "L", 10 to "X", 9 to "IX",
                                  5 to "V", 4 to "IV", 1 to "I"))
            while (v >= num) { sb.append(sym); v -= num }
        return if (sb.isEmpty()) n.toString() else sb.toString()
    }

    // ---------- biome vocab ----------

    data class Vocab(val ambient: List<String>, val adj: List<String>, val noun: List<String>)

    private val VOCAB = mapOf(
        "crypt" to Vocab(
            listOf(
                "Water counts the seconds somewhere below.",
                "A draught carries the smell of old incense.",
                "The torch writes long shadows on the bone-dry walls.",
                "Dust settles on everything like patient snow.",
                "Far off, something small stops moving when the torch stops."),
            listOf("gaunt", "dust-caked", "hollow-eyed", "grave-lean", "quiet"),
            listOf("alcove", "crypt", "ossuary", "tomb", "vault")),
        "fungal" to Vocab(
            listOf(
                "Spores drift past the torch like slow snow.",
                "Something luminous breathes in the dark.",
                "The ground gives softly underfoot, like skin.",
                "Sweet rot and rain — the hollow exhales.",
                "A grove of caps glows faintly between the roots."),
            listOf("moss-slicked", "pale-capped", "spore-heavy", "overgrown", "weeping"),
            listOf("grove", "hollow", "thicket", "grotto", "clearing")),
        "ember" to Vocab(
            listOf(
                "Heat pulses up through the seams of the floor.",
                "Far off, metal groans as it cools.",
                "Ash hangs in the air, unhurried.",
                "The walls sweat dull orange where the torch passes.",
                "Somewhere below, the forge that never sleeps turns another hour."),
            listOf("slag-crusted", "ash-grey", "smoke-stained", "heat-cracked", "cinder-flecked"),
            listOf("shaft", "gallery", "seam", "smeltery", "drift")),
        "drowned" to Vocab(
            listOf(
                "Black water swallows the torchlight whole.",
                "Drips answer one another across the flooded hall.",
                "Something huge shifts its weight below the surface, and settles.",
                "Salt rust on every railing — the halls remember ships."),
            listOf("waterlogged", "brine-eaten", "tide-marked", "slimed", "cold-soaked"),
            listOf("hall", "cistern", "causeway", "floodway", "depths")),
        "void" to Vocab(
            listOf(
                "The stars overhead are not stars.",
                "Footsteps arrive a moment after they are made.",
                "The dark here is load-bearing.",
                "A whisper recites something in no language, patiently.",
                "Distance feels like a rumor in this light."),
            listOf("lightless", "unmoored", "star-scribed", "nameless", "silent"),
            listOf("reach", "expanse", "gallery", "threshold", "hollow")))

    private fun vocab(biome: Biome): Vocab = VOCAB[biome.id] ?: VOCAB["crypt"]!!

    fun biomeNoun(biome: Biome): String = pick("noun:" + biome.id, vocab(biome).noun)
    fun biomeAdj(biome: Biome): String = pick("adj:" + biome.id, vocab(biome).adj)

    fun ambient(biome: Biome): String =
        pick("ambient:" + biome.id, vocab(biome).ambient)

    // ---------- monster epithets ----------

    private val EPI_DETAIL = listOf(
        "cracked teeth", "one milky eye", "notched claws", "a limp it hides well",
        "a crown of broken bone", "old rope around the wrists", "burn scars",
        "a hunger that shows", "too many joints", "a voice like wet gravel")
    private val EPI_CLAUSE = listOf(
        "patient as stone", "hungry in a quiet way", "humming to itself",
        "counting something", "watching the torch, not the hero", "smiling wrong",
        "older than the stairs", "certain of the outcome")
    private val EPI_TPL = listOf(
        "a {0} {1}", "a {1} with {2}", "a {1}, {3}",
        "an old {1} with {2}", "a {0} {1}, {3}")

    fun epithet(m: MType, biome: Biome): String {
        val v = vocab(biome)
        return fill(pick("epiTpl", EPI_TPL),
            pick("epiAdj", v.adj), m.name,
            pick("epiDetail", EPI_DETAIL), pick("epiClause", EPI_CLAUSE))
    }

    // ---------- first sight / warden intro / taunts ----------

    private val FIRST_SIGHT = listOf(
        "Something moves in the {0} — {1}.",
        "Movement in the {0}: {1} turns toward the light.",
        "{1} separates from the dark.",
        "First sight: {1}. It has not decided about the torch yet.",
        "The torch catches {1}, waiting between one step and the next.")

    fun firstSight(m: MType, biome: Biome): String {
        val v = vocab(biome)
        return line("firstSight", FIRST_SIGHT,
            pick("noun:" + biome.id, v.noun), epithet(m, biome))
    }

    private val WARDEN_IN = listOf(
        "{0} turns toward the torch — the floor's keeper has noticed.",
        "The air goes cold. {0} has been waiting on these stairs a long time.",
        "{0} unfolds from stillness. The floor's rent is due.")

    fun wardenIntro(name: String): String = line("wardenIn", WARDEN_IN, name)

    private val TAUNT = listOf(
        "The {0} hisses something old and hungry.",
        "The {0} knows the hero's name, somehow.",
        "A sound from the {0} — almost words, mostly want.",
        "The {0} stops pretending to be scenery.")

    fun taunt(name: String): String = line("taunt", TAUNT, name)

    // ---------- combat narration ----------

    private val KILL = listOf(
        "{0} falls, and does not rise.",
        "One clean stroke — {0} is done.",
        "{0} breaks apart at the hero's feet.",
        "The torch swings once; {0} is already behind it, undone.",
        "{0} learns the last lesson the deep teaches.")
    private val KILL_CRIT = listOf(
        "The blade finds the seam — {0} comes apart in one bright moment.",
        "A perfect strike. {0} is a story the floor tells now.",
        "{0} never even finishes the lunge.")
    private val KILL_BOSS = listOf(
        "{0} falls, and the whole floor seems to exhale.",
        "The keeper is unkept. The way down is open.",
        "{0} breaks — and something old lets go of the stairs.")

    fun kill(m: MType, subject: String, crit: Boolean): String = when {
        m.boss -> line("killBoss", KILL_BOSS, subject)
        crit   -> line("killCrit", KILL_CRIT, subject)
        else   -> line("kill", KILL, subject)
    }

    private val HERO_HIT = listOf(
        "The {0} lands a blow — {1} through the guard.",
        "Claws find their mark. {1}.",
        "The {0} hits like a slammed door — {1}.",
        "{1}, paid to the {0}'s appetite.")

    fun heroHit(name: String, dmg: Int): String = line("heroHit", HERO_HIT, name, dmg)

    // ---------- hero narration ----------

    private val POTIONS = listOf(
        "quaffs a potion — {0}/{1} hp, and the shaking stops.",
        "drinks deep — {0}/{1} hp.",
        "a potion, down in one — {0}/{1} hp.")

    fun potion(hp: Int, maxHp: Int): String = line("potion", POTIONS, hp, maxHp)

    private val POTION_LOOT = listOf(
        "turns the remains over — a potion, still sealed.",
        "a potion on the body; the deep provides.",
        "the dead carry kindness sometimes — a potion.")

    fun potionLoot(): String = pick("potionLoot", POTION_LOOT)

    private val LEVELS = listOf(
        "The deep takes note — level {0}.",
        "Strength returns in a slow tide — level {0}.",
        "Old hurts knit into new muscle — level {0}.")

    fun levelUp(level: Int): String = line("level", LEVELS, level)

    private val LOW_HP = listOf(
        listOf("Breath comes ragged now; the torchlight looks thinner.",
               "Half the red is gone. The stair home is a very long idea."),
        listOf("Blood in the boot. The deep leans in to watch.",
               "Every shadow has an opinion now."),
        listOf("One more hit, and the deep keeps another name.",
               "The lantern is a coin-flip between blows."))

    fun lowHp(stage: Int): String = line("low$stage", LOW_HP[stage.coerceIn(0, 2)])

    private val STREAK3 = listOf(
        "Three in as many breaths — the dark is learning the hero's shape.")
    private val STREAK5 = listOf(
        "Five without an answer — the floor itself seems to flinch.")

    fun streak(n: Int): String =
        if (n == 3) pick("streak3", STREAK3) else pick("streak5", STREAK5)

    // ---------- story arc ----------

    private val MOTIVES = listOf(
        "No one asked them to come down. That is usually how it starts.",
        "The deep owes the surface a debt; they mean to collect.",
        "They are looking for the stair that has no bottom.",
        "Someone above is waiting, and does not know it yet.",
        "A promise was made at the mouth of these stairs, and the deep heard it.")

    fun prologue(legacyName: String?, legacyDepth: Int): List<String> {
        val out = ArrayList<String>()
        out.add(pick("motive", MOTIVES))
        if (legacyName != null) out.add(
            fill("In the pack: a lantern that once belonged to {0}, fallen at floor {1}. The plan is simple — go further.",
                 legacyName, legacyDepth))
        return out
    }

    private val CHAPTER_SUB = mapOf(
        "crypt" to listOf("where bones remember their names", "the debt of the buried",
                          "patience, carved in stone"),
        "fungal" to listOf("the green that waits", "spores and slow intentions",
                           "life, gone strange"),
        "ember" to listOf("the forge that never sleeps", "heat keeps the old vows",
                          "iron remembers fire"),
        "drowned" to listOf("the tide keeps its schedule", "what sank was not lost",
                            "the halls drink the light"),
        "void" to listOf("the stars here are wrong", "distance is a rumor",
                         "the dark is load-bearing"))

    fun chapter(n: Int, biome: Biome): Pair<String, String> {
        val sub = pick("sub:" + biome.id, CHAPTER_SUB[biome.id] ?: listOf(biome.tagline))
        return "— Chapter ${roman(n)} · ${biome.name} —" to "${biome.icon} $sub"
    }

    private val DESCENT_WORD = mapOf(
        "crypt" to "cold stone", "fungal" to "wet loam", "ember" to "smoke and iron",
        "drowned" to "brine", "void" to "old static")

    fun descent(depth: Int, biome: Biome): String =
        fill("descends to floor {0} — the air tastes of {1}.",
             depth, DESCENT_WORD[biome.id] ?: "old stone")

    private val ELEGY_OPEN = listOf(
        "Here ends {0} — {1}.",
        "So ends {0}, {1}.",
        "{0} stops here — {1}.")
    private val ELEGY_REASON = listOf(
        "who asked the deep a question it did not like",
        "who went one floor past wisdom",
        "who carried the light as far as it would go",
        "whom the stairs will keep",
        "who nearly made the dark blink first")
    private val ELEGY_CLOSE = listOf(
        "The deep is {0} floors quieter. Or louder. It is hard to tell.",
        "Floor {0} keeps what it caught.",
        "Somewhere above, a lantern is still owed.")
    private val EPITAPHS = listOf(
        "asked the deep for more",
        "faster than their luck",
        "the dark keeps what it catches",
        "one floor from forever",
        "went down, and kept going")

    fun epitaph(): String = pick("epitaph", EPITAPHS)

    fun elegy(name: String, s: RunSummary): List<String> {
        val out = ArrayList<String>()
        out.add(line("elegyOpen", ELEGY_OPEN, name, pick("elegyReason", ELEGY_REASON)))
        s.journal.takeLast(3).forEach { out.add("· $it") }
        out.add(line("elegyClose", ELEGY_CLOSE, s.depth))
        return out
    }

    // ---------- learning narration ----------

    fun brainLesson(param: String, increased: Boolean): String {
        val phrase = when (param) {
            "aggression" -> if (increased) "grows bolder in the melee" else "grows careful in the melee"
            "panic"      -> if (increased) "learns to drink earlier" else "learns to stretch its wounds further"
            "healGate"   -> if (increased) "trusts mending sooner" else "walks hurt a little longer"
            "nukeSave"   -> if (increased) "hoards its strong arts" else "spends its strong arts freely"
            "greed"      -> if (increased) "walks farther for shine" else "chases only nearby shine"
            "reserve"    -> if (increased) "keeps a heavier purse" else "lets coin move faster"
            "descent"    -> if (increased) "reads floors more fully before the stairs" else "takes the stairs sooner"
            "skirt"      -> if (increased) "gives wardens a wider berth" else "hugs the keepers' shadows"
            "finish"     -> if (increased) "hunts wounded things first" else "strikes where honor points"
            "elite"      -> if (increased) "covets crowned heads" else "lets crowned heads pass"
            "boss"       -> if (increased) "fears the floor's keeper" else "squares up to the keeper"
            "grit"       -> if (increased) "counts the cost of blood less" else "counts the cost of blood more"
            "stairBias"  -> if (increased) "leans toward the deep" else "lingers on the floors"
            "skillBias"  -> if (increased) "trusts its arts more" else "hoards its arts"
            else -> "carries a new lesson"
        }
        return "the lineage $phrase"
    }

    fun deathLesson(param: String): String = when (param) {
        "skirt"      -> "paid for in blood — the lineage gives wardens a wider berth"
        "boss"       -> "paid for in blood — the lineage learns the keepers are not duels"
        "reserve"    -> "died dry — the lineage keeps a heavier purse for potions"
        "panic"      -> "died hoarding — the lineage learns to drink before the shaking starts"
        "aggression" -> "died surrounded — the lineage learns crowds are not courage"
        "healGate"   -> "died of venom — the lineage learns to mend sooner"
        else -> "carries the lesson forward"
    }

    // ---------- personality ----------

    fun personaIntro(title: String, epithet: String): String = pick("psI", listOf(
        "this life: $title — $epithet.",
        "$title walks these stairs. $epithet.",
        "born again as $title; $epithet."))

    fun personaWhim(title: String): String = pick("psW", listOf(
        "$title way: forward.",
        "so says $title.",
        "$title logic — she acts on it."))

    // ---------- genetics & the learned ledger ----------

    fun genomeBirth(origin: String, champion: String): String = when (origin) {
        "heir" -> pick("gnH", listOf(
            "the champion's blood runs true — heir to $champion.",
            "born of the strongest line: $champion's heir."))
        "cross" -> pick("gnC", listOf(
            "bred from two strong lines — the pool takes its chances.",
            "two bloodlines meet in this one."))
        "immigrant" -> pick("gnI", listOf(
            "a stranger's blood joins the pool — the lineage gambles on new blood.",
            "no ancestry the deep remembers. Perhaps that is the advantage."))
        else -> "a new line begins, untested."
    }

    fun genomeScored(fitness: Int): String = pick("gnS", listOf(
        "the line scores $fitness — the pool remembers",
        "$fitness, carved into the ledger of blood"))

    fun mdpOverride(action: String): String = pick("mdpO", listOf(
        "the learned ledger prefers $action — against the old instinct",
        "experience over instinct: $action"))

    fun mdpMilestone(n: Int, states: Int): String =
        "the ledger of choices holds $n learned judgments across $states situations"

    // ---------- the tactical tree ----------

    fun mctsAhead(type: String): String = pick("mcA", listOf(
        "she reads the room a few steps ahead — the obvious line into the $type was not the safe one.",
        "a quieter path to the $type — seen before it was walked."))

    fun mctsEscape(type: String): String = pick("mcE", listOf(
        "the way out runs through the $type — sometimes the exit is a kill.",
        "she cuts through the $type; around is longer than through."))

    fun mctsMilestone(n: Int, overrides: Int): String =
        "🌳 $n tactical maps drawn — $overrides times the sure step was not the true one"

    // ---------- AI foresight & mood ----------

    fun forethought(reason: String): String = pick("ft", listOf(
        "the math says no — $reason.",
        "she weighs it, and steps back — $reason.",
        "$reason; the torch retreats."))

    fun loreMarked(name: String): String = pick("lmk", listOf(
        "the lineage marks the $name — far more dangerous than it looks",
        "scar-tissue memory: the $name hits above its station"))

    fun loreDisarmed(name: String): String = pick("lda", listOf(
        "the lineage stops fearing the $name — its bark exceeds its bite",
        "old fear set aside: the $name proves manageable"))

    fun moodLine(m: String): String = when (m) {
        "WOUNDED" -> pick("mdW", listOf(
            "blood loss narrows the world to exits and flasks.",
            "wounded — every plan gets shorter."))
        "HUNTED" -> pick("mdH", listOf(
            "hunted — the dark has opinions and teeth.",
            "something is following; her steps quicken."))
        "BLOODIED" -> pick("mdB", listOf(
            "blooded and grinning — the streak talks.",
            "the torch burns hotter after a good kill."))
        "WEARY" -> pick("mdY", listOf(
            "weary — this floor has given all it will get.",
            "the torch is low in more ways than one."))
        else -> ""
    }

    fun wardenHunt(): String = pick("wh", listOf(
        "the warden stops waiting — it walks the halls now, hunting.",
        "heavy steps begin circling the floor — the keeper hunts."))

    fun wardenWarn(): String = pick("ww", listOf(
        "the stairwell exhales something vast — a keeper waits below.",
        "the air on the next floor is already angry."))

    // ---------- uniqueness: elites & quirks ----------

    fun eliteIntro(name: String, affix: String, type: String, hint: String): String =
        line("eliteIn", listOf(
            "{0}, {1} {2}, bars the way. ({3})",
            "Out of the dark comes {0}, a {1} {2} — {3}.",
            "{0} was here before the torch was lit. A {1} {2}, {3}."),
            name, affix, type, hint.ifEmpty { "it watches" })

    fun eliteKill(name: String): String = pick("eliteK", listOf(
        "{0} is felled — the floor breathes easier.",
        "{0} goes down swinging, and the swinging stops.",
        "{0} falls with its name still on its lips.")).let { fill(it, name) }

    fun ambush(type: String): String = pick("amb", listOf(
        "The {0} was waiting — the first blow lands twice as hard.",
        "An ambush! The {0} strikes from hiding.")).let { fill(it, type) }

    fun venomHit(): String = pick("ven", listOf(
        "Venom sinks in — the wound will fester.",
        "A sting, and then the slow fire of venom."))

    fun poisonTick(): String = pick("pT", listOf(
        "The venom works quietly.",
        "The bite throbs with a small cold heat."))

    fun poisonEnd(): String = "The venom finishes its work."

    fun stealLine(type: String, amt: Int): String = pick("stl", listOf(
        "The {0} slips {1} gold from the purse mid-swing.",
        "Light fingers — the {0} makes off with {1} gold.")).let { fill(it, type, amt) }

    fun enrage(type: String): String = pick("enr", listOf(
        "The {0} forgets caution — it fights hurt, and fights harder.",
        "Blood in its eyes: the {0} is enraged.")).let { fill(it, type) }

    fun fleeLine(type: String): String = pick("fle", listOf(
        "The {0} turns tail for the dark.",
        "The {0} breaks and runs — craven to the last.")).let { fill(it, type) }

    fun undyingLine(type: String): String = pick("und", listOf(
        "The {0} refuses the first death, and stands back up.",
        "It should be dead. The {0} disagrees.")).let { fill(it, type) }

    fun packHowl(type: String): String = pick("pkh", listOf(
        "The {0} calls — and something answers.",
        "A cry from the {0}; its kin stir in the dark.")).let { fill(it, type) }

    fun patronExtra(): String = pick("pat", listOf(
        "slips in a little extra. 'For the road.'",
        "adds more than was promised, watching the dark as they do."))

    // ---------- items ----------

    fun itemPickup(i: Item): String = when (i.rarity) {
        Rarity.COMMON -> pick("itC", listOf(
            "pockets the ${i.name} (${i.passiveLine()})",
            "tucks the ${i.name} away (${i.passiveLine()})"))
        Rarity.UNCOMMON -> pick("itU", listOf(
            "turns the ${i.name} over twice, then keeps it (${i.passiveLine()})",
            "the ${i.name} — better than it looks (${i.passiveLine()})"))
        Rarity.RARE -> pick("itR", listOf(
            "the ${i.name} — rare as honesty down here (${i.passiveLine()})",
            "real work went into the ${i.name} (${i.passiveLine()})"))
        Rarity.EPIC -> pick("itE", listOf(
            "holds the ${i.name} up to the torch, briefly reverent (${i.passiveLine()})",
            "the ${i.name} — the deep made very few of these (${i.passiveLine()})"))
        Rarity.LEGENDARY -> pick("itL", listOf(
            "lifts the ${i.name} like a vow (${i.passiveLine()})",
            "the ${i.name}. Songs start here (${i.passiveLine()})"))
    }

    fun equip(i: Item): String {
        val verb = when (i.slot) {
            ItemSlot.WEAPON -> "wields"
            ItemSlot.ARMOR -> "fastens on"
            else -> "hangs at the neck"
        }
        val flavor = when (i.rarity) {
            Rarity.LEGENDARY -> pick("eqL", listOf("the torch leans toward it.", "old light, still warm."))
            Rarity.EPIC -> pick("eqE", listOf("a cut above.", "worth its weight in stairs."))
            else -> ""
        }
        val base = "$verb the ${i.name} — ${i.passiveLine()}"
        return if (flavor.isEmpty()) base else "$base. $flavor"
    }

    fun relicPickup(r: Item): String = pick("rlP", listOf(
        "claims the ${r.name} — it hums once and is quiet (${r.passiveLine()})",
        "the ${r.name} presses itself into service (${r.passiveLine()})"))

    // ---------- skills ----------

    fun learnSkill(name: String): String = pick("skLearn", listOf(
        "adds the art of {0} to her craft",
        "writes {0} into the margins of the map",
        "borrows {0} from a braver idea",
        "practices {0} until the dark stops laughing")).let { fill(it, name) }

    fun skillCast(school: School, name: String, target: String, dmg: Int): String {
        val flavor = when (school) {
            School.BLADE -> "steel answers"
            School.EMBER -> "words of ash"
            School.TIDE  -> "the water remembers"
            School.BLOOM -> "green things lean in"
            School.VOID  -> "something vast listens"
        }
        return pick("skCast", listOf(
            "{0} — {1} takes the {2} for {3}.",
            "{0}: {1} finds the {2}. {3} damage.",
            "{0}. {1}, and the {2} pays {3}."
        )).let { fill(it, flavor, name, target, dmg) }
    }

    fun skillSelf(name: String, fx: SkillFx, amount: Int, dur: Int): String = when (fx) {
        SkillFx.HEAL    -> fill(pick("skHeal", listOf(
            "{0} — light knits into the wounds (+{1} hp).",
            "{0} closes what the dark opened (+{1} hp).")), name, amount)
        SkillFx.BUFF_ATK-> fill(pick("skBufA", listOf(
            "{0} — strength gathers like a held breath (+{1} atk, {2} turns).",
            "{0}. The next few rooms should be afraid (+{1} atk, {2} turns).")), name, amount, dur)
        SkillFx.BUFF_DEF-> fill(pick("skBufD", listOf(
            "{0} — the skin remembers stone (+{1} def, {2} turns).",
            "{0}. Let them break their teeth (+{1} def, {2} turns).")), name, amount, dur)
        SkillFx.BLINK   -> fill(pick("skBlink", listOf(
            "{0} — the space between steps folds, and she is elsewhere.",
            "{0}. The room keeps her outline; she does not.")), name)
        SkillFx.POTION  -> fill("{0} — calls a potion from somewhere kind.", name)
        else            -> "{0} settles over her."
    }

    fun skillStun(name: String, target: String): String = fill(pick("skStun", listOf(
        "{0} — the {1} reels, briefly a statue.",
        "{0} holds the {1} in place, if only for a moment."
    )), name, target)

    fun thornsKill(target: String): String = pick("thornK", listOf(
        "the {0} dies of its own bad idea — the hero's spines saw to it.",
        "the {0} pushes too hard and is undone by the hero's returning spite."
    )).let { fill(it, target) }

    // ---------- camps, curses, pacts ----------

    fun floorMod(m: FloorMod): String = when (m) {
        FloorMod.THICK -> pick("fmT", listOf(
            "🌙 Thick Dark — the torch reaches a pace, and no further.",
            "🌙 the dark here is thick; sight fails early."))
        FloorMod.BLOOD -> pick("fmB", listOf(
            "🩸 Blood Tide — more fangs among the halls, and more gold on them.",
            "🩸 the floor is hungry today. Crowns outnumber kindness."))
        FloorMod.HUSH -> pick("fmH", listOf(
            "🤫 The Hush — no wind, no drips; hearing carries too far.",
            "🤫 silence with texture — they will hear you before they see you."))
        FloorMod.GILDED -> pick("fmG", listOf(
            "💰 Gilded Halls — old wealth dusts every surface.",
            "💰 gold in the mortar, gold in the dust."))
    }

    fun campfireFound(): String = pick("cfF", listOf(
        "🔥 a campfire burns where the stairs empty — a rare mercy. Three roads of rest:"))
    fun campfirePick(name: String): String = pick("cfP", listOf(
        "🔥 she chooses $name, and the fire accepts.",
        "🔥 $name — chosen without ceremony; the fire needs none."))

    fun pactPaid(toll: Int, warden: String): String = pick("pcP", listOf(
        "🤝 $toll gold changes hands — the $warden steps aside, and the pact holds.",
        "🤝 the $warden weighs the coin, then the hero. The coin wins. Passage: $toll gold."))

    // ---------- dungeon features ----------

    fun trapRevealed(): String = pick("tpR", listOf(
        "a hair-thin wire glints in the torchlight — a trap, marked and avoided.",
        "the floor here is scored with old violence — a trap, seen just in time."))

    fun trapSpikes(dmg: Int): String = pick("tpS", listOf(
        "spikes punch up through the seams — $dmg.",
        "the wire sings; the wall answers — $dmg."))

    fun trapGas(): String = pick("tpG", listOf(
        "a hiss — green gas rolls over her; the venom takes hold.",
        "the air turns sweet and wrong — poison in the lungs."))

    fun trapGlyph(): String = pick("tpY", listOf(
        "the glyph flares — the floor rearranges itself around her.",
        "one step, and the room is somewhere else entirely."))

    fun chestMimic(): String = pick("chM", listOf(
        "the chest grows teeth — it was never a chest.",
        "lid, hinges, hunger — a mimic uncoils."))

    fun chestForced(i: Item): String = pick("chF", listOf(
        "the lock breaks — inside: ${i.name} (${i.passiveLine()})",
        "brute force, old hinge, ${i.name} — worth the bruises (${i.passiveLine()})"))

    fun chestRefused(): String = pick("chR", listOf(
        "the lock holds; her ribs matter more — left for later.",
        "she tests the hasp once and steps away — not at this price."))

    fun shrineBoon(what: String): String = pick("shB", listOf(
        "the shrine answers — $what.",
        "prayer, received: $what."))

    fun shrineCurse(what: String): String = pick("shC", listOf(
        "the shrine's face changes — $what.",
        "the stone drinks, and gives back ruin: $what."))

    // ---------- bounties & the run in numbers ----------

    fun bountyPost(name: String, type: String, gold: Int): String = pick("bP", listOf(
        "🎯 WANTED: $name, a $type of rare nerve — $gold gold on its head.",
        "🎯 the deep posts a bounty: $name the $type. $gold gold to whoever collects."))

    fun bountyClaim(name: String, gold: Int): String = pick("bC", listOf(
        "🎯 the bounty on $name is paid — $gold gold, and the floor is quieter.",
        "🎯 $name's head-price collected: $gold gold."))

    fun recapLines(dealt: Int, taken: Int, bigHit: Int, bigTaken: Int,
                   potions: Int, skills: Int, earned: Int, spent: Int): List<String> = listOf(
        "· the run, in numbers —",
        "· struck for $dealt (best $bigHit) · took $taken (worst $bigTaken)",
        "· flasks drained: $potions · arts cast: $skills",
        "· gold: +$earned earned · −$spent spent")

    // ---------- enforcement & time ----------

    fun darkWarning(): String = pick("dw", listOf(
        "⚠️ THE DARK ADVANCES — the hero is not safe while you look away.",
        "⚠️ the pact of stillness is broken — what hunts, keeps hunting."))

    fun stipend(): String = pick("stP", listOf(
        "💰 the rivals walk your floor unopposed — their purses fill.",
        "💰 untouched rooms, unclaimed loot — the rivals feast."))

    fun auraUp(tier: Int): String = when (tier) {
        1 -> "🔥 kindled — the focus aura grants +25% xp"
        2 -> "🔥🔥 burning — +50% xp · +1 sight"
        3 -> "🔥🔥🔥 everflame — +100% xp · +2 sight"
        else -> "✨ transcendent — the deep grants a relic on the next stair"
    }

    fun auraDrop(): String = pick("ad", listOf(
        "🕯️ the aura gutters — distraction cost two tiers of flame",
        "🕯️ the flame dips — the deep noticed the looking away"))

    fun catchUp(mins: Long): String =
        "🕯️ the torch held on its own for $mins minutes — the rivals did not sleep"

    // ---------- rivals ----------

    fun rivalRumor(name: String): String = pick("rvR", listOf(
        "Torchlight that isn't yours flickers in a far gallery — $name is delving this floor too.",
        "Boot-steps not your own, going deeper. $name is down here somewhere."))

    fun rivalIntro(name: String, tag: String, hint: String): String =
        line("rvI", listOf(
            "You meet torchlight: $name, $tag — $hint.",
            "$name, $tag, nods once across the hall. ($hint)",
            "A rival delver: $name, $tag. $hint."))

    fun rivalHit(name: String, dmg: Int): String = pick("rvH", listOf(
        "Steel meets steel — you take the $name exchange for $dmg.",
        "$name's blade finds a seam — $dmg."))

    fun rivalHitsYou(name: String, dmg: Int): String = pick("rvY", listOf(
        "$name presses the attack — $dmg through your guard.",
        "$name fights like the floor is a grudge — $dmg."))

    fun rivalDown(name: String, hoard: Int): String = pick("rvD", listOf(
        "$name falls. The rivalry ends; the $hoard gold doesn't.",
        "One last exchange, and $name is done — $hoard gold changes hands."))

    fun rivalCut(name: String, type: String): String = pick("rvC", listOf(
        "$name cuts down a $type without slowing.",
        "A $type dies in the dark — $name's work."))

    fun rivalPocket(name: String, item: String): String = pick("rvP", listOf(
        "$name pockets the $item before you can blink.",
        "The $item is gone — $name's boots were faster."))

    fun rivalDescend(name: String, depth: Int, ahead: Boolean): String =
        if (ahead) "word drifts up the stairwell: $name is now on floor $depth — below you."
        else "$name takes the stairs down to floor $depth."

    fun drawAhead(name: String): String = pick("rvA", listOf(
        "the lineage draws ahead of $name.",
        "$name's torch is now above yours. For now."))

    // ---------- familiars & sessions ----------

    fun familiarJoin(name: String, kind: String): String = pick("fJ", listOf(
        "a $kind darts from the dark and adopts her — '$name', she decides.",
        "$name the $kind falls in step, uninvited and permanent."))
    fun familiarBite(name: String): String = pick("fB", listOf(
        "$name snaps at its heels.", "$name earns its keep — a nip."))
    fun familiarGold(name: String): String = pick("fG", listOf(
        "$name roots out a few loose coins.", "$name returns proud of something shiny."))
    fun familiarHides(name: String): String =
        "$name hangs back — it knows better than to face a keeper."
    fun auraKindle(): String =
        "the focus aura kindles — the deep teaches faster while you hold the line."
    fun sessionDone(): String = pick("sD", listOf(
        "🎯 session complete — the deep pays its respect, and a relic.",
        "🎯 the session held. The deep keeps its word."))
}
