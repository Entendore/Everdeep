package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class NpcKind(val glyph: Char, val color: Int, val oneTime: Boolean) {
    GIFT('¥', 0xFFF0A35C.toInt(), true),
    HEALER('Ω', 0xFF9FD08A.toInt(), true),
    LORE('¿', 0xFFB39DDB.toInt(), true),
    SMITH('Þ', 0xFFB8A98C.toInt(), false),
    MAPKEEPER('Ø', 0xFFC9C2B0.toInt(), false),
    MERCHANT('ϖ', 0xFFE0B84F.toInt(), false),
    GAMBLER('?', 0xFFE0B84F.toInt(), false),
    GHOST('Ý', 0xFF9AA3B2.toInt(), true)
}

enum class Temper(val priceMult: Float) {
    GENEROUS(0.8f), FAIR(1.0f), GREEDY(1.45f), MAD(1.0f), CRYPTIC(1.1f)
}

class Offer(val item: Item, val price: Int)

class Npc(
    val kind: NpcKind, var x: Int, var y: Int,
    val name: String, val title: String, val temper: Temper,
    val description: String, val greeting: String,
    val rumors: List<String>,
    val offer: Offer? = null,
    val ghostDepth: Int = 0,
    val quirk: NpcQuirk = NpcQuirk.NONE
) {
    var greeted = false
}

class RunFacts(
    val depth: Int, val biome: Biome, val wardenName: String?,
    val heroKills: Int, val fallenName: String?, val fallenDepth: Int,
    val heroGold: Int, val heroPotions: Int, val exploredPct: Float
)

object NpcForge {

    private val ONSET = listOf("b","br","d","dr","f","g","gr","h","k","kr","l",
                               "m","n","r","s","sh","t","th","v","w","z")
    private val NUCLEUS = listOf("a","e","i","o","u","ae","ei","ia","ou","y")
    private val CODA = listOf("","n","r","s","l","th","nd","m","rn","st","ss")

    private fun syllable() = ONSET.random() + NUCLEUS.random() + CODA.random()

    fun name(): String {
        val n = syllable() + syllable() + if (Random.nextInt(100) < 35) syllable() else ""
        return n.replaceFirstChar { it.uppercase() }
    }

    private val TITLES = mapOf(
        NpcKind.GIFT to listOf("the Lantern-Bearer", "of Warm Pockets", "the Unprompted",
            "Who Shares the Flame", "the Small Kindness", "Keeper of Spare Wicks"),
        NpcKind.HEALER to listOf("the Still", "of Quiet Hands", "Who Binds with Water",
            "the Unburdened", "the Soft Answer", "Mender of What Bites"),
        NpcKind.LORE to listOf("the Thrice-Told", "Who Reads Ash", "Keeper of Small Truths",
            "the Answered", "the Footnote", "Who Remembers Wrong on Purpose"),
        NpcKind.SMITH to listOf("Hammerfall", "the Second Anvil", "of Living Iron",
            "Who Argues with Metal", "the Last Grind", "Spark-Keeper"),
        NpcKind.MAPKEEPER to listOf("the Surveyor", "of Inked Veins", "Who Counts the Walls",
            "the Cartographer of Ruin", "the Honest Scale", "Straight-Line Walker"),
        NpcKind.MERCHANT to listOf("of the Nineteen Wares", "the Ledgered", "Coin-Blessed",
            "of the Under-Market", "Who Sells to Both Sides", "the Reasonable Price", "Purse-Reader"),
        NpcKind.GAMBLER to listOf("Two-Dice", "the Houseless", "Fortune's Floor-Mate",
            "the Loaded", "Who Bets on Strangers", "Snake-Eyed"),
        NpcKind.GHOST to listOf("the Quiet Tenant", "Who Kept Going", "Unfinished", "Who Fell Before"))

    private fun title(kind: NpcKind, biome: Biome, ghostDepth: Int): String {
        if (kind == NpcKind.GHOST && ghostDepth > 0 && Random.nextBoolean())
            return "Who Fell at Floor $ghostDepth"
        if (kind != NpcKind.GHOST && Random.nextInt(100) < 30) {
            return when (Random.nextInt(4)) {
                0 -> "of the ${Narrator.biomeNoun(biome)} Roads"
                1 -> "Who Sang to the ${Narrator.biomeNoun(biome)}"
                2 -> "the ${Narrator.biomeAdj(biome)} Pilgrim"
                else -> "Exile of the ${Narrator.biomeNoun(biome)}"
            }
        }
        return TITLES[kind]!!.random()
    }

    private val BUILD = listOf("a broad", "a stooped", "a narrow", "a wiry",
                               "a gaunt", "a heavy-set", "a bent")
    private val WEAR = listOf("in patchwork leathers", "in a coat of many pockets",
        "in robes gone grey", "in soot-black wool", "under a banner-cloak",
        "in scales stitched like mail", "under layers of scarves")
    private val DETAIL = listOf("one eye weeps tar", "teeth filed to points",
        "fingers stained with ink", "a brass key on a cord", "a scar like a river delta",
        "a caged firefly at the belt", "boots resoled with shield-rim",
        "a smile like a closed door", "knuckles tattooed with tally marks",
        "a birdskull on a thong")
    private val HABIT = listOf("hums an off-key hymn", "counts coins with closed eyes",
        "talks to the torch", "never blinks at the right time", "smells of myrrh and rust",
        "shadows the steps of nobody at all", "keeps one hand cupped behind an ear")

    private fun description(depth: Int): String {
        val days = 3 + Random.nextInt(7 + depth * 5)
        return "${BUILD.random()} figure ${WEAR.random()}; ${DETAIL.random()}, " +
               "and ${HABIT.random()}, $days days below"
    }

    private val GREET = mapOf(
        Temper.GENEROUS to listOf("'Take what you need,' they say, already reaching.",
                                  "'The dark taxes everyone. Not today, friend.'"),
        Temper.FAIR to listOf("'Fair trade, fair light.'",
                              "'State your want. I'll state the price.'"),
        Temper.GREEDY to listOf("Eyes your purse before your face.",
                                "'Everything has a price. Even listening.'"),
        Temper.MAD to listOf("'The stair counts you! It counts everyone!'",
                             "'Don't drink the dark. People do. They do.'"),
        Temper.CRYPTIC to listOf("'You were taller the last time.' (You have never been here.)",
                                 "'Ah. Early, late — the floor can't tell anymore.'"))

    fun refuseLine(npc: Npc, cost: Int): String = when (npc.temper) {
        Temper.GENEROUS -> "'Come back with anything. It'll be enough.'"
        Temper.FAIR -> "'$cost gold. No discounts — the dark discounts enough.'"
        Temper.GREEDY -> "'$cost gold. Sentiment is free, but coin is coin.'"
        Temper.MAD -> pick("refM", listOf(
            "'The fish know. They've always known.' (The price remains unclear.)",
            "'$cost gold, or a favor I will name later. Probably the gold.'"))
        Temper.CRYPTIC -> "'Maybe later. Maybe already. The ledger is confused.'"
    }

    private fun kindLine(kind: NpcKind): String = when (kind) {
        NpcKind.MERCHANT -> pick("klM", listOf(
            "'Prices adjust to the dark. The dark adjusts back.'",
            "'Everything's for sale. Even advice: don't.'"))
        NpcKind.SMITH -> pick("klS", listOf(
            "'Metal remembers every blow. I make sure it remembers good ones.'",
            "'Bring me your dents. I've seen worse in mirrors.'"))
        NpcKind.MAPKEEPER -> pick("klK", listOf(
            "'I drew this floor once. The floor has opinions about my ink.'",
            "'Maps are just promises the stone hasn't read yet.'"))
        NpcKind.GAMBLER -> pick("klG", listOf(
            "'The dice don't lie. They just mislead.'",
            "'Odds are a kind of prayer.'"))
        NpcKind.HEALER -> pick("klH", listOf(
            "'I bind what the dark unmakes. Breathing is free.'",
            "'Hold still. The dark hates an audience.'"))
        NpcKind.GIFT -> pick("klB", listOf(
            "'Small lights against a big dark. Take one.'",
            "'Kindness compounds. Ask anyone I've helped.'"))
        NpcKind.LORE -> pick("klL", listOf(
            "'Ask me anything. I know three true things and one beautiful lie.'",
            "'History down here is written by whoever survives the sentence.'"))
        NpcKind.GHOST -> pick("klX", listOf(
            "'…' (the dead are brief.)",
            "'It is very quiet where I am now. I recommend nothing.'"))
    }

    private fun backstory(kind: NpcKind): String = when (kind) {
        NpcKind.MERCHANT -> pick("bsM", listOf(
            "claims to have sold to things with too many teeth",
            "was banished from two surface markets and one below it",
            "keeps a ledger of everyone who ever owed them"))
        NpcKind.SMITH -> pick("bsS", listOf(
            "learned the craft forging hinges for coffin-lids",
            "says every blade they ever made has come back down here",
            "once shod a warden's boots and won't say how"))
        NpcKind.MAPKEEPER -> pick("bsK", listOf(
            "has drawn this floor before, in someone else's blood",
            "was lost for a season and came back knowing things",
            "sells copies and keeps the truth"))
        NpcKind.GAMBLER -> pick("bsG", listOf(
            "lost a name at dice and won it back twice",
            "was struck by lightning and now trusts only chance",
            "owes the house — is the house"))
        NpcKind.HEALER -> pick("bsH", listOf(
            "buried a patient once, and never again",
            "learned their craft from something that was not a doctor",
            "heals the deep's creatures too, when they ask politely"))
        NpcKind.GIFT -> pick("bsB", listOf(
            "lights one lantern per floor for someone no one remembers",
            "came down looking for a sibling and stayed for everyone",
            "was saved once by a stranger and is paying it forward forever"))
        NpcKind.LORE -> pick("bsL", listOf(
            "has read every stone inscription twice and cried once",
            "is writing a history no one will live to finish",
            "was a warden's keeper once, and will not say which warden"))
        NpcKind.GHOST -> pick("bsX", listOf(
            "kept going one floor too long",
            "trusted the wrong staircase",
            "almost made it — 'almost' is doing heavy work"))
    }

    private fun rumors(kind: NpcKind, biome: Biome, f: RunFacts, quirk: NpcQuirk): List<String> {
        val out = ArrayList<String>()
        out.add(kindLine(kind))
        out.add(backstory(kind))
        if (f.wardenName != null && Random.nextInt(100) < 70) out.add(pick("ruW", listOf(
            "'The ${f.wardenName} keeps this floor. Walk soft; it keeps count.'",
            "'${f.wardenName} has taken three torches this season. Make it expensive.'")))
        if (f.fallenName != null && Random.nextInt(100) < 60) out.add(pick("ruF", listOf(
            "'${f.fallenName} came through here going down. ${f.fallenDepth} floors — that's all a torch buys sometimes.'",
            "'You carry ${f.fallenName}'s look. Fell at ${f.fallenDepth}. Finish it differently.'")))
        if (f.heroKills >= 8 && Random.nextInt(100) < 40)
            out.add("'Word travels even down here — ${f.heroKills} dead behind you. The deep reads footprints.'")
        if (Random.nextInt(100) < 40) out.add(Narrator.ambient(biome))
        if (quirk == NpcQuirk.DOOMSAYER && f.wardenName != null)
            out.add(0, "'The ${f.wardenName} waits at the deep end of this floor. Count your blows.'")
        return out
    }

    private fun offer(kind: NpcKind, depth: Int, t: Temper): Offer? {
        if (kind != NpcKind.MERCHANT) return null
        fun price(base: Int) = (base * t.priceMult).toInt().coerceAtLeast(4)
        return when (Random.nextInt(100)) {
            in 0 until 45 -> Items.randomOf(depth).let { Offer(it, price(it.price * 2 + 6)) }
            in 45 until 65 -> Offer(
                Item("two sealed potions", ItemSlot.POTION, '!', Rarity.COMMON, potions = 2, price = 10),
                price(16 + depth * 3))
            in 65 until 80 -> Offer(
                Item("an elixir of marrow", ItemSlot.POTION, '!', Rarity.UNCOMMON, hp = 4, heal = 4, price = 18),
                price(20 + depth * 3))
            in 80 until 90 -> Offer(
                Item("a whetstone of good steel", ItemSlot.RELIC, '*', Rarity.UNCOMMON, atk = 1, price = 14),
                price(15 + depth * 2))
            else -> Offer(
                Item("an old charm of warding", ItemSlot.RELIC, '*', Rarity.UNCOMMON, def = 1, price = 14),
                price(15 + depth * 2))
        }
    }

    fun build(kind: NpcKind, biome: Biome, depth: Int, facts: RunFacts, x: Int, y: Int): Npc {
        val temper = Temper.entries.random()
        val quirk = if (Random.nextInt(100) < 80)
            NpcQuirk.entries.filter { it != NpcQuirk.NONE }.random() else NpcQuirk.NONE
        val nm = if (kind == NpcKind.GHOST) facts.fallenName ?: name() else name()
        val desc = description(depth) +
            if (quirk != NpcQuirk.NONE) " — a known ${quirk.tag}" else ""
        return Npc(kind, x, y, nm, title(kind, biome, facts.fallenDepth), temper,
            desc, GREET[temper]!!.random(),
            rumors(kind, biome, facts, quirk),
            offer(kind, depth, temper),
            ghostDepth = facts.fallenDepth,
            quirk = quirk)
    }

    private fun pick(key: String, v: List<String>) = Narrator.pick(key, v)

    fun giftLine() = pick("dGift", listOf(
        "'The dark is worse when you're dry,' they say — a potion changes hands.",
        "presses a potion and a warm smile into the hero's hands."))
    fun healLine() = pick("dHeal", listOf(
        "lays cool hands on the hero's brow — every hurt closes like water.",
        "says nothing at all, and the wounds are simply gone."))
    fun loreLine(xp: Int) = pick("dLore", listOf(
        "tells one true thing about the deep — it lands like armor. (+$xp xp)",
        "recites a half-finished history with the hero in it. (+$xp xp)"))
    fun smithAtkLine() = pick("dSmA", listOf(
        "hammers the edge keener — +1 atk.",
        "'Hold it like you hate it,' he says — +1 atk."))
    fun smithDefLine() = pick("dSmD", listOf(
        "rivets a plate onto the guard — +1 def.",
        "'Dents are cheaper than funerals,' they say — +1 def."))
    fun mapLine(depth: Int) = pick("dMap", listOf(
        "unrolls a sooty chart — floor $depth lies open to the eye.",
        "chalks the halls onto a scrap of hide. 'Mind the deep end.'"))
    fun refuseGambleLine() = pick("dGmR", listOf(
        "won't roll the dice for less than 8 gold.",
        "shakes the cup. 'Empty purses play no part.'"))
    fun gambleJackpot(g: Int) = pick("dGmJ", listOf(
        "throws the dice — they sing! +$g gold.",
        "snake eyes for the house! +$g gold."))
    fun gamblePotionLine() = pick("dGmP", listOf(
        "grins — the dice come up potion-shaped.",
        "hands over a bottle instead of winnings. 'Same thing.'"))
    fun gambleVitalityLine() = pick("dGmV", listOf(
        "wins the hero a strange vitality — +4 max hp.",
        "'That one's on the house,' they say. The hero feels taller. +4 max hp."))
    fun gambleRobLine() = pick("dGmL", listOf(
        "sweeps the coin away with a laugh — the dice betray.",
        "'House always wins,' they say, not sorry."))
    fun ghostLine(fallen: String, depth: Int, xp: Int) = pick("dGh", listOf(
        "\"The deep took me at floor $depth,\" whispers the ghost of $fallen. \"Take my strength.\" (+$xp xp)",
        "$fallen drifts alongside a moment, pointing out safer ground. (+$xp xp)"))

    fun departLine(kind: NpcKind): String = when (kind) {
        NpcKind.GIFT -> pick("dpG", listOf("walks on into the dark, humming.",
                                           "waves once, already leaving."))
        NpcKind.HEALER -> pick("dpH", listOf("fades into stillness.",
                                             "is simply elsewhere when the torch turns."))
        NpcKind.LORE -> pick("dpL", listOf("closes the book mid-sentence and wanders off.",
                                           "disperses into the stacks of the dark."))
        NpcKind.GHOST -> pick("dpX", listOf("drifts apart like smoke.",
                                            "disperses with something like relief."))
        else -> pick("dpO", listOf("turns back to their work.",
                                   "lets the dark fold around them again."))
    }
}
