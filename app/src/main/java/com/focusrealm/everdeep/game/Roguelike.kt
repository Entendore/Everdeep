package com.focusrealm.everdeep.game

import kotlin.math.abs
import kotlin.random.Random

enum class Tone { PLAIN, GOOD, BAD, EPIC }

data class Pt(val x: Int, val y: Int)

enum class Tile { WALL, FLOOR, STAIRS }

data class MType(val id: String, val glyph: Char, val name: String,
                 val hp: Int, val atk: Int, val def: Int,
                 val xp: Int, val gold: Int,
                 val boss: Boolean = false, val tier: Int = 0,
                 val color: Int = 0xFFE9DFC8.toInt())

data class RunSummary(val depth: Int, val level: Int, val kills: Int, val gold: Int,
                      val turns: Int, val slainBy: String = "the dark",
                      val journal: List<String> = emptyList(),
                      val epitaph: String = "")

enum class Mood { DELIBERATE, HUNTED, WOUNDED, BLOODIED, WEARY }

enum class FloorMod(val tag: String) {
    THICK("Thick Dark"), BLOOD("Blood Tide"), HUSH("The Hush"), GILDED("Gilded Halls")
}

class Monster(val type: MType, var x: Int, var y: Int, depth: Int = 1, biome: Biome? = null) {
    private val hpVar = 0.85f + Random.nextFloat() * 0.3f
    private val atkVar = 0.85f + Random.nextFloat() * 0.3f
    val maxHp: Int
    val atk: Int
    val xpReward = type.xp + depth / 2
    val goldReward = type.gold + depth / 3
    var hp: Int
    var aggro = false
    var stun = 0
    val alive get() = hp > 0
    val epithet: String =
        if (biome != null && !type.boss) Narrator.epithet(type, biome) else ""

    var quirk: Quirk? = null
    var affix: Affix? = null
    var elite = false
    var eliteName = ""
    var introDone = false
    var enraged = false
    var fledTaunt = false
    var ambushed = false
    var undyingUsed = false
    var hoard = 0
    var bounty = 0
    var bribed = false

    init {
        maxHp = ((type.hp + depth / 3) * hpVar).toInt().coerceAtLeast(3)
        atk = ((type.atk + depth / 5) * atkVar).toInt().coerceAtLeast(1)
        hp = maxHp
    }

    fun effectiveAtk(): Int = when {
        affix == Affix.VICIOUS || enraged -> (atk * 1.4f).toInt()
        else -> atk
    }
    fun effectiveDef(): Int = if (affix == Affix.IRONHIDE) type.def + 3 else type.def
    fun goldDrop(): Int {
        var g = goldReward + hoard
        if (quirk == Quirk.HOARDER) g = (g * 1.6f).toInt()
        if (elite) g = (g * 1.6f).toInt()
        return g
    }
    fun xpDrop(): Int = if (elite) (xpReward * 1.5f).toInt() else xpReward
}

class FloorItem(val x: Int, val y: Int, val item: Item, val amount: Int = 0) {
    val glyph: Char get() = if (item.slot == ItemSlot.GOLD) '$' else item.glyph
}

enum class FeatKind { SPIKES, GAS, GLYPH, CHEST, LOCKED, SHRINE }

class FloorFeat(val kind: FeatKind, val x: Int, val y: Int) {
    var revealed = kind !in listOf(FeatKind.SPIKES, FeatKind.GAS, FeatKind.GLYPH)
    var used = false
    val glyph: Char get() = when (kind) {
        FeatKind.CHEST, FeatKind.LOCKED -> '¤'
        FeatKind.SHRINE -> 'Ω'
        else -> '^'
    }
}

class Familiar(val kind: String, val glyph: Char, val color: Int, var x: Int, var y: Int) {
    val name = NpcForge.name()
    var cd = 0
}

class Hero(var x: Int, var y: Int) {
    var maxHp = 24; var hp = 24
    var atk = 4; var def = 1
    var level = 1; var xp = 0; var xpNext = 20
    var potions = 1; var gold = 0; var kills = 0

    var pCrit = 0; var pThorns = 0; var pLifesteal = 0
    var pVision = 0; var pPotion = 0; var pGold = 0; var pXp = 0

    val skills = ArrayList<Skill>()
    val cooldowns = HashMap<String, Int>()
    var buffAtk = 0; var buffAtkT = 0
    var buffDef = 0; var buffDefT = 0

    var weapon: Item? = null; var armor: Item? = null; var trinket: Item? = null

    val effAtk get() = atk + buffAtk
    val effDef get() = def + buffDef
}

class Engine(val W: Int = 44, val H: Int = 30) {

    companion object { const val RUN_SCHEMA = 2 }

    interface Bridge {
        fun log(text: String, tone: Tone)
        fun onDepth(depth: Int)
        fun onLevelUp(level: Int)
        fun onFallen(summary: RunSummary)
        fun onTurnEnd(hero: Hero, depth: Int, turns: Int)
        fun onLesson(text: String)
        fun onRivalEvent(name: String, glyph: Char, depth: Int, dead: Boolean)
        fun onEmber(n: Int) {}
        fun onDiscoverMonster(id: String) {}
        fun onMonsterKilled(id: String) {}
        fun onDiscoverNpc(kind: String) {}
        fun onDiscoverItem(name: String) {}
    }

    var bridge: Bridge? = null

    var map: Array<Array<Tile>> = arrayOf()
    var explored: Array<BooleanArray> = arrayOf()
    var visible: Array<BooleanArray> = arrayOf()
    var decor: Array<CharArray> = arrayOf()
    var biome: Biome = Biomes.CRYPT
    var hero = Hero(0, 0)
    var stairs = Pt(0, 0)
    val monsters = ArrayList<Monster>()
    val items = ArrayList<FloorItem>()
    val npcs = ArrayList<Npc>()
    val rivals = ArrayList<Rival>()
    val feats = ArrayList<FloorFeat>()
    val knownItems = HashSet<Int>()
    var depth = 1
    var turns = 0
    var heroAlive = true
    var heroName = ""

    var legacyName: String? = null
    var legacyDepth = 0
    fun setLegacy(name: String?, fallenDepth: Int) { legacyName = name; legacyDepth = fallenDepth }

    // ---------- learning stack ----------
    val brain = HeroBrain()
    val lore = MonsterLore()
    val mdp = HeroMDP()
    val mcts = TactMCTS()
    var mctsBudget = 110
    var genome: Genome? = null
    var personality: Personality = Personality(Temperament.BOLD, Focus.BLADE)
    private fun t(name: String): Float = personality.adjusted(name, brain.f(name))

    fun inherit(g: Genome) {
        genome = g
        brain.absorb(g.values)
    }

    // ---------- run state ----------
    val journal = ArrayList<String>()
    val chronicle = Chronicle()
    val runStats = HashMap<String, Int>()
    val featEvents = ArrayList<Pair<String, Int>>()
    val boonQueue = ArrayList<String>()
    private val appliedTalents = HashMap<String, Int>()
    private val fightLog = HashMap<String, Int>()
    private val seenTypes = HashSet<String>()
    private val metNpcKinds = HashSet<String>()
    private val npcMemory = HashSet<Npc>()
    private val ignoredFeats = HashSet<FloorFeat>()
    private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    private val MDX = intArrayOf(0, 0, 0, -1, 1)
    private val MDY = intArrayOf(0, -1, 1, 0, 0)

    private var floorStamp = 0
    private var lastBiomeId: String? = null
    private var chapterCount = 0
    private var killStreak = 0
    private var bestStreak = 0
    private var rivalsFelled = 0
    private var firstBlood = false
    private var nearDeathSaid = false
    private var tauntCd = 0
    private var ambientCd = 30
    private var said50 = false; private var said25 = false; private var said12 = false
    private var heroPoison = 0
    private var darkKill = false
    private var mood = Mood.DELIBERATE
    private var turnsThisFloor = 0
    private var wardenHunting = false
    private var bribedThisFloor = false
    private var verdictReason: String? = null
    private var lootLuck = 0
    var auraXp = 0f
    var auraVision = 0
    var familiar: Familiar? = null
    private var familiarHid = false
    private var floorMod: FloorMod? = null
    private var lastMdpState = -1L
    private var lastMdpAction = -1
    private val activeSlots = 6

    private fun note(s: String) {
        journal.add(s)
        if (journal.size > 16) journal.removeAt(0)
    }
    private fun chron(tag: String, text: String) { chronicle.add(turns, depth, tag, text) }

    // ---------- run lifecycle ----------

    fun newRun(name: String) {
        heroName = name
        depth = 1
        turns = 0
        heroAlive = true
        hero = Hero(0, 0)
        lastBiomeId = null
        chapterCount = 0
        killStreak = 0; bestStreak = 0; rivalsFelled = 0
        firstBlood = false; nearDeathSaid = false
        tauntCd = 0; ambientCd = 30
        said50 = false; said25 = false; said12 = false
        heroPoison = 0; darkKill = false
        mood = Mood.DELIBERATE
        turnsThisFloor = 0; wardenHunting = false; bribedThisFloor = false
        verdictReason = null
        lastMdpAction = -1; lastMdpState = -1
        featEvents.clear(); runStats.clear()
        appliedTalents.clear()
        journal.clear(); seenTypes.clear(); metNpcKinds.clear()
        familiar = null; familiarHid = false
        chronicle.reset()
        genFloor()
        bridge?.log("${biome.icon} ${biome.name} — ${biome.tagline}", Tone.PLAIN)
        Narrator.prologue(legacyName, legacyDepth).forEach { bridge?.log(it, Tone.PLAIN) }
        bridge?.log(Narrator.personaIntro(personality.title, personality.temperament.epithet), Tone.EPIC)
        chronicle.personaTitle = personality.title
        chronicle.add(0, 1, "start", "$heroName takes the first stair")
        val (act, pas) = Skills.startPair()
        learnSkill(act, "carries")
        learnSkill(pas, "carries")
        bridge?.onTurnEnd(hero, depth, turns)
    }

    private fun genFloor() {
        floorStamp++
        biome = Biomes.forDepth(depth)
        map = Array(H) { Array(W) { Tile.WALL } }
        explored = Array(H) { BooleanArray(W) }
        visible = Array(H) { BooleanArray(W) }
        decor = Array(H) { CharArray(W) { ' ' } }
        monsters.clear(); items.clear(); npcs.clear(); rivals.clear()
        feats.clear(); npcMemory.clear(); knownItems.clear(); ignoredFeats.clear()
        nearDeathSaid = false
        turnsThisFloor = 0; wardenHunting = false; bribedThisFloor = false
        floorMod = if (depth >= 2 && Random.nextInt(100) < 30)
            FloorMod.entries.random() else null

        when (biome.gen) {
            Gen.BSP   -> genBSP()
            Gen.CAVES -> genCaves()
            Gen.MINES -> genMines()
            Gen.RUINS -> genRuins()
        }

        var start = Pt(W / 2, H / 2)
        var best = Int.MAX_VALUE
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            val d = abs(x - W / 2) + abs(y - H / 2)
            if (d < best) { best = d; start = Pt(x, y) }
        }
        if (best == Int.MAX_VALUE) { fallbackCarve(); start = Pt(W / 2, H / 2) }
        hero.x = start.x; hero.y = start.y

        val dist = bfsDist(start)
        var far = start
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            if (dist[key(x, y)] > dist[key(far.x, far.y)]) far = Pt(x, y)
        }
        map[far.y][far.x] = Tile.STAIRS
        stairs = far
        val maxD = dist[key(far.x, far.y)]

        paintDecor()
        spawnMonsters(dist, maxD, far)
        spawnItems(dist, far)
        spawnNpcs(dist)
        spawnRivals(dist)
        spawnFeats(dist)
        spawnVault(dist, maxD)
        recomputeVision()
        floorMod?.let { bridge?.log(Narrator.floorMod(it), Tone.BAD) }
    }

    // ---------- generators ----------

    private fun genBSP() {
        val leaves = ArrayList<IntArray>()
        fun split(x: Int, y: Int, w: Int, h: Int, d: Int) {
            val canV = w >= 14
            val canH = h >= 11
            if (d <= 0 || (!canV && !canH)) { leaves.add(intArrayOf(x, y, w, h)); return }
            if (canV && (!canH || Random.nextBoolean())) {
                val cut = Random.nextInt(6, w - 6)
                split(x, y, cut, h, d - 1); split(x + cut, y, w - cut, h, d - 1)
            } else {
                val cut = Random.nextInt(5, h - 5)
                split(x, y, w, cut, d - 1); split(x, y + cut, w, h - cut, d - 1)
            }
        }
        split(1, 1, W - 2, H - 2, 4)
        val centers = ArrayList<Pt>()
        for (l in leaves) {
            val lw = l[2]; val lh = l[3]
            val rw = Random.nextInt(3, maxOf(4, lw - 2))
            val rh = Random.nextInt(3, maxOf(4, lh - 2))
            val rx = l[0] + Random.nextInt(1, maxOf(2, lw - rw - 1))
            val ry = l[1] + Random.nextInt(1, maxOf(2, lh - rh - 1))
            carve(rx, ry, rw, rh)
            centers.add(Pt(rx + rw / 2, ry + rh / 2))
        }
        for (i in 1 until centers.size) tunnel(centers[i - 1], centers[i])
        if (centers.size > 3) tunnel(centers[0], centers[centers.size - 1])
    }

    private fun genCaves() {
        var attempts = 0
        while (attempts < 5) {
            attempts++
            var g = Array(H) { y ->
                IntArray(W) { x ->
                    if (x == 0 || y == 0 || x == W - 1 || y == H - 1) 1
                    else if (Random.nextInt(100) < 45) 1 else 0
                }
            }
            repeat(4) { g = smoothCave(g) }
            for (y in 0 until H) for (x in 0 until W)
                map[y][x] = if (g[y][x] == 1) Tile.WALL else Tile.FLOOR
            val regs = regions()
            if (regs.isEmpty() || regs[0].size < W * H / 8) continue
            val main = regs[0]
            for (i in 1 until regs.size) tunnel(regs[i].random(), main.random())
            return
        }
        fallbackCarve()
    }

    private fun smoothCave(g: Array<IntArray>): Array<IntArray> {
        val out = Array(H) { IntArray(W) }
        for (y in 0 until H) for (x in 0 until W) {
            if (x == 0 || y == 0 || x == W - 1 || y == H - 1) { out[y][x] = 1; continue }
            var walls = 0
            for (dy in -1..1) for (dx in -1..1) {
                val yy = y + dy; val xx = x + dx
                if (yy < 0 || xx < 0 || yy >= H || xx >= W || g[yy][xx] == 1) walls++
            }
            out[y][x] = if (walls >= 5) 1 else 0
        }
        return out
    }

    private fun regions(): List<MutableList<Pt>> {
        val seen = Array(H) { BooleanArray(W) }
        val out = ArrayList<MutableList<Pt>>()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] == Tile.WALL || seen[y][x]) continue
            val reg = ArrayList<Pt>()
            val q = ArrayDeque<Pt>()
            q.add(Pt(x, y)); seen[y][x] = true
            while (q.isNotEmpty()) {
                val p = q.removeFirst()
                reg.add(p)
                for (d in DIRS) {
                    val nx = p.x + d[0]; val ny = p.y + d[1]
                    if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
                    if (map[ny][nx] == Tile.WALL || seen[ny][nx]) continue
                    seen[ny][nx] = true
                    q.add(Pt(nx, ny))
                }
            }
            out.add(reg)
        }
        out.sortByDescending { it.size }
        return out
    }

    private fun genMines() {
        var x = W / 2; var y = H / 2
        map[y][x] = Tile.FLOOR
        repeat(6 + depth % 4) {
            var dir = DIRS.random()
            repeat(Random.nextInt(18, 36)) {
                if (Random.nextInt(100) < 30) {
                    val wv = DIRS.random()
                    map[(y + wv[1]).coerceIn(1, H - 2)][(x + wv[0]).coerceIn(1, W - 2)] = Tile.FLOOR
                }
                if (Random.nextInt(100) < 18) dir = DIRS.random()
                x = (x + dir[0]).coerceIn(1, W - 2)
                y = (y + dir[1]).coerceIn(1, H - 2)
                map[y][x] = Tile.FLOOR
                if (Random.nextInt(100) < 5) {
                    val rw = Random.nextInt(3, 6); val rh = Random.nextInt(2, 5)
                    carve((x - rw / 2).coerceIn(1, W - 2 - rw),
                          (y - rh / 2).coerceIn(1, H - 2 - rh), rw, rh)
                }
            }
        }
    }

    private fun genRuins() {
        val centers = ArrayList<Pt>()
        repeat(10 + Random.nextInt(4)) {
            val w = Random.nextInt(4, 10); val h = Random.nextInt(3, 7)
            val x = Random.nextInt(1, W - w - 1); val y = Random.nextInt(1, H - h - 1)
            carve(x, y, w, h)
            centers.add(Pt(x + w / 2, y + h / 2))
        }
        for (i in 1 until centers.size) tunnelWide(centers[i - 1], centers[i])
        if (centers.size > 4) tunnelWide(centers[0], centers[centers.size - 1])
    }

    private fun fallbackCarve() {
        map = Array(H) { Array(W) { Tile.WALL } }
        carve(4, 4, 14, 8)
        carve(W / 2 - 6, H / 2 - 3, 12, 6)
        carve(W - 20, H - 12, 14, 8)
        tunnel(Pt(11, 8), Pt(W / 2, H / 2))
        tunnel(Pt(W / 2, H / 2), Pt(W - 13, H - 8))
    }

    private fun carve(x: Int, y: Int, w: Int, h: Int) {
        for (yy in y until y + h) for (xx in x until x + w) map[yy][xx] = Tile.FLOOR
    }

    private fun tunnel(a: Pt, b: Pt) {
        if (Random.nextBoolean()) { hCorr(a.x, b.x, a.y); vCorr(a.y, b.y, b.x) }
        else { vCorr(a.y, b.y, a.x); hCorr(a.x, b.x, b.y) }
    }

    private fun hCorr(x1: Int, x2: Int, y: Int) {
        for (x in minOf(x1, x2)..maxOf(x1, x2))
            if (x in 1 until W - 1 && y in 1 until H - 1) map[y][x] = Tile.FLOOR
    }

    private fun vCorr(y1: Int, y2: Int, x: Int) {
        for (y in minOf(y1, y2)..maxOf(y1, y2))
            if (x in 1 until W - 1 && y in 1 until H - 1) map[y][x] = Tile.FLOOR
    }

    private fun tunnelWide(a: Pt, b: Pt) {
        if (Random.nextBoolean()) { hWide(a.x, b.x, a.y); vWide(a.y, b.y, b.x) }
        else { vWide(a.y, b.y, a.x); hWide(a.x, b.x, b.y) }
    }

    private fun hWide(x1: Int, x2: Int, y: Int) {
        for (x in minOf(x1, x2)..maxOf(x1, x2))
            for (yy in y..y + 1)
                if (x in 1 until W - 1 && yy in 1 until H - 1) map[yy][x] = Tile.FLOOR
    }

    private fun vWide(y1: Int, y2: Int, x: Int) {
        for (y in minOf(y1, y2)..maxOf(y1, y2))
            for (xx in x..x + 1)
                if (xx in 1 until W - 1 && y in 1 until H - 1) map[y][xx] = Tile.FLOOR
    }

    internal fun bfsDist(from: Pt): IntArray {
        val dist = IntArray(W * H) { -1 }
        val q = ArrayDeque<Pt>()
        dist[key(from.x, from.y)] = 0
        q.add(from)
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            val dp = dist[key(p.x, p.y)]
            for (d in DIRS) {
                val nx = p.x + d[0]; val ny = p.y + d[1]
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                if (map[ny][nx] == Tile.WALL) continue
                if (dist[key(nx, ny)] >= 0) continue
                dist[key(nx, ny)] = dp + 1
                q.add(Pt(nx, ny))
            }
        }
        return dist
    }

    private fun paintDecor() {
        for (y in 0 until H) for (x in 0 until W) decor[y][x] = ' '
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            for ((g, chance, _) in biome.decor)
                if (Random.nextFloat() < chance) { decor[y][x] = g; break }
        }
        for (c in biome.clusters) {
            repeat(c.count) {
                var x = Random.nextInt(2, W - 2)
                var y = Random.nextInt(2, H - 2)
                repeat(c.size) {
                    if (map[y][x] == Tile.FLOOR) decor[y][x] = c.glyph
                    x = (x + Random.nextInt(-1, 2)).coerceIn(1, W - 2)
                    y = (y + Random.nextInt(-1, 2)).coerceIn(1, H - 2)
                }
            }
        }
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.WALL) continue
            val touchesFloor = DIRS.any { map[y + it[1]][x + it[0]] != Tile.WALL }
            if (!touchesFloor) continue
            for ((g, chance, _) in biome.wallDecor)
                if (Random.nextFloat() < chance) { decor[y][x] = g; break }
        }
    }

    // ---------- spawning ----------

    private fun pickMonsterType(): MType {
        val cycle = (depth - 1) / 15
        val inBiome = (depth - 1) % 3
        var tier = (cycle + inBiome).coerceAtMost(3)
        when (Random.nextInt(100)) {
            in 0 until 15  -> tier = (tier - 1).coerceAtLeast(0)
            in 15 until 40 -> tier = (tier + 1).coerceAtMost(3)
        }
        return biome.pool.filter { it.tier == tier }.ifEmpty { biome.pool }.random()
    }

    private fun spawnMonsters(dist: IntArray, maxD: Int, stairsPt: Pt) {
        val candidates = ArrayList<Pt>()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            if (dist[key(x, y)] >= 8) candidates.add(Pt(x, y))
        }
        candidates.shuffle()
        val count = minOf(16, 5 + depth / 2)
        var placed = 0
        for (c in candidates) {
            if (placed >= count) break
            if (monsterAt(c.x, c.y) != null) continue
            val nm = Monster(pickMonsterType(), c.x, c.y, depth, biome)
            dressUp(nm)
            monsters.add(nm)
            placed++
        }
        if (depth % 3 == 0) {
            val spots = candidates.filter {
                dist[key(it.x, it.y)] >= maxD * 2 / 3 &&
                !(it.x == stairsPt.x && it.y == stairsPt.y) &&
                monsterAt(it.x, it.y) == null
            }
            spots.randomOrNull()?.let { monsters.add(Monster(warden(), it.x, it.y, depth, biome)) }
        }
    }

    private fun dressUp(m: Monster) {
        if (m.type.boss) return
        if (Random.nextInt(100) < (if (floorMod == FloorMod.BLOOD) 24 else 12)) {
            m.elite = true
            m.eliteName = EliteForge.name()
            m.affix = Affix.entries.random()
            m.quirk = Quirk.entries.random()
            when (m.affix) {
                Affix.COLOSSAL -> { m.maxHp = (m.maxHp * 1.6f).toInt(); m.hp = m.maxHp }
                Affix.ANCIENT  -> { m.maxHp = (m.maxHp * 1.3f).toInt(); m.hp = m.maxHp }
                else -> {}
            }
        } else if (Random.nextInt(100) < 35) {
            m.quirk = Quirk.entries.random()
        }
        if (depth >= 4 && Random.nextInt(100) < 8) {
            if (!m.elite) {
                m.elite = true
                m.eliteName = EliteForge.name()
                m.affix = Affix.entries.random()
                m.quirk = Quirk.entries.random()
                if (m.affix == Affix.COLOSSAL) { m.maxHp = (m.maxHp * 1.6f).toInt(); m.hp = m.maxHp }
            }
            m.bounty = 40 + depth * 8
            bridge?.log(Narrator.bountyPost(m.eliteName, m.type.name, m.bounty), Tone.BAD)
        }
    }

    private fun spawnItems(dist: IntArray, stairsPt: Pt) {
        val spots = ArrayList<Pt>()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            if (dist[key(x, y)] < 3) continue
            if (x == stairsPt.x && y == stairsPt.y) continue
            spots.add(Pt(x, y))
        }
        spots.shuffle()
        fun place(item: Item, amount: Int = 0) {
            val idx = spots.indexOfFirst { s ->
                items.none { it.x == s.x && it.y == s.y } &&
                monsterAt(s.x, s.y) == null && npcAt(s.x, s.y) == null
            }
            if (idx < 0) return
            val s = spots.removeAt(idx)
            items.add(FloorItem(s.x, s.y, item, amount))
        }
        place(Items.potionRoll(depth))
        repeat(5 + Random.nextInt(4)) {
            if (Random.nextInt(100) < 25) place(Items.GOLD, 6 + depth + Random.nextInt(10))
            else place(Items.randomOf(depth, lootLuck))
        }
    }

    private fun runFacts(): RunFacts = RunFacts(
        depth, biome, if (depth % 3 == 0) warden().name else null,
        hero.kills, legacyName, legacyDepth,
        hero.gold, hero.potions, exploredFraction())

    private fun pickNpcKind(first: Boolean): NpcKind {
        if (first && legacyName != null && Random.nextInt(100) < 20) return NpcKind.GHOST
        val pool = ArrayList<NpcKind>()
        pool.add(NpcKind.GIFT); pool.add(NpcKind.GIFT)
        pool.add(NpcKind.HEALER); pool.add(NpcKind.LORE)
        pool.add(NpcKind.MERCHANT); pool.add(NpcKind.MERCHANT)
        pool.add(NpcKind.SMITH); pool.add(NpcKind.MAPKEEPER)
        pool.add(NpcKind.GAMBLER)
        return pool.random()
    }

    private fun spawnNpcs(dist: IntArray) {
        if (Random.nextInt(100) >= 70) return
        val facts = runFacts()
        val count = when (Random.nextInt(100)) {
            in 0 until 55 -> 1
            in 55 until 85 -> 2
            else -> 3
        }
        repeat(count) { i ->
            val kind = pickNpcKind(i == 0)
            val minD = if (i == 0) 10 else 6
            val spots = ArrayList<Pt>()
            for (y in 1 until H - 1) for (x in 1 until W - 1) {
                if (map[y][x] != Tile.FLOOR) continue
                if (dist[key(x, y)] < minD) continue
                if (x == hero.x && y == hero.y) continue
                if (map[y][x] == Tile.STAIRS) continue
                if (monsterAt(x, y) != null || npcAt(x, y) != null || rivalAt(x, y) != null) continue
                if (items.any { it.x == x && it.y == y }) continue
                if (feats.any { it.x == x && it.y == y }) continue
                spots.add(Pt(x, y))
            }
            spots.randomOrNull()?.let { npcs.add(NpcForge.build(kind, biome, depth, facts, it.x, it.y)) }
        }
    }

    private fun spawnRivals(dist: IntArray) {
        if (depth < 2) return
        val chance = if (depth >= 8) 55 else 40
        if (Random.nextInt(100) >= chance) return
        val spots = ArrayList<Pt>()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            if (dist[key(x, y)] < 12) continue
            if (monsterAt(x, y) != null || npcAt(x, y) != null) continue
            if (map[y][x] == Tile.STAIRS) continue
            if (items.any { it.x == x && it.y == y }) continue
            spots.add(Pt(x, y))
        }
        spots.randomOrNull()?.let {
            rivals.add(RivalForge.build(it.x, it.y, depth))
            if (Random.nextInt(100) < 50)
                bridge?.log(Narrator.rivalRumor(rivals.last().name), Tone.PLAIN)
        }
    }

    private fun spawnFeats(dist: IntArray) {
        val count = minOf(3, Random.nextInt(0, 2) + depth / 6)
        repeat(count) {
            val spots = ArrayList<Pt>()
            for (y in 1 until H - 1) for (x in 1 until W - 1) {
                if (map[y][x] != Tile.FLOOR) continue
                if (dist[key(x, y)] < 6) continue
                if (map[y][x] == Tile.STAIRS) continue
                if (monsterAt(x, y) != null || npcAt(x, y) != null || rivalAt(x, y) != null) continue
                if (items.any { it.x == x && it.y == y }) continue
                if (feats.any { it.x == x && it.y == y }) continue
                spots.add(Pt(x, y))
            }
            val s = spots.randomOrNull() ?: return
            val kind = when (Random.nextInt(100)) {
                in 0 until 30 -> when (Random.nextInt(3)) {
                    0 -> FeatKind.SPIKES; 1 -> FeatKind.GAS; else -> FeatKind.GLYPH
                }
                in 30 until 55 -> FeatKind.CHEST
                in 55 until 70 -> FeatKind.LOCKED
                else -> FeatKind.SHRINE
            }
            feats.add(FloorFeat(kind, s.x, s.y))
        }
    }

    private fun spawnVault(dist: IntArray, maxD: Int) {
        if (depth < 3 || Random.nextInt(100) >= 20) return
        val spots = ArrayList<Pt>()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] != Tile.FLOOR) continue
            if (dist[key(x, y)] < maxD * 3 / 4) continue
            if (monsterAt(x, y) != null || npcAt(x, y) != null || rivalAt(x, y) != null) continue
            spots.add(Pt(x, y))
        }
        val c = spots.randomOrNull() ?: return
        items.removeAll { it.x == c.x && it.y == c.y }
        val guard = Monster(pickMonsterType(), c.x, c.y, depth, biome)
        guard.elite = true
        guard.eliteName = EliteForge.name()
        guard.affix = Affix.entries.random()
        guard.quirk = Quirk.entries.random()
        if (guard.affix == Affix.COLOSSAL) { guard.maxHp = (guard.maxHp * 1.6f).toInt(); guard.hp = guard.maxHp }
        monsters.add(guard)
        var placed = 0
        for (dir in DIRS) {
            if (placed >= 3) break
            val nx = c.x + dir[0]; val ny = c.y + dir[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] != Tile.FLOOR) continue
            if (items.any { it.x == nx && it.y == ny } || monsterAt(nx, ny) != null) continue
            items.add(FloorItem(nx, ny, Items.randomOf(depth + 2, lootLuck)))
            placed++
        }
    }

    private fun warden() = MType("warden", 'W', biome.wardenName,
        26 + depth * 6, 6 + depth, 2, 30 + depth * 5, 30 + depth * 4,
        boss = true, tier = 9, color = 0xFFC4483C.toInt())

    // ---------- vision ----------

    private fun recomputeVision() {
        for (row in visible) row.fill(false)
        val r = (7 + hero.pVision + auraVision -
            (if (floorMod == FloorMod.THICK) 2 else 0)).coerceAtLeast(4)
        for (dy in -r..r) for (dx in -r..r) {
            if (dx * dx + dy * dy > r * r) continue
            val tx = hero.x + dx; val ty = hero.y + dy
            if (tx < 0 || ty < 0 || tx >= W || ty >= H) continue
            if (lineOfSight(hero.x, hero.y, tx, ty)) {
                visible[ty][tx] = true
                explored[ty][tx] = true
            }
        }
        for (itm in items) if (visible[itm.y][itm.x]) knownItems.add(key(itm.x, itm.y))
    }

    private fun lineOfSight(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        while (true) {
            if (x == x1 && y == y1) return true
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
            if (x == x1 && y == y1) return true
            if (map[y][x] == Tile.WALL) return false
        }
    }

    private fun revealNearbyTraps() {
        for (f in feats) {
            if (f.revealed || f.used) continue
            if (manhattan(f.x, f.y, hero.x, hero.y) <= 1) {
                f.revealed = true
                bridge?.log(Narrator.trapRevealed(), Tone.BAD)
            }
        }
    }

    // ---------- one turn ----------

    fun heroTurn() {
        if (!heroAlive) return
        turns++
        brain.onTurn()
        turnsThisFloor++
        if (hero.cooldowns.isNotEmpty())
            hero.cooldowns.keys.toList().forEach { k ->
                val v = hero.cooldowns[k] ?: 0
                if (v <= 1) hero.cooldowns.remove(k) else hero.cooldowns[k] = v - 1
            }
        if (hero.buffAtkT > 0 && --hero.buffAtkT == 0) hero.buffAtk = 0
        if (hero.buffDefT > 0 && --hero.buffDefT == 0) hero.buffDef = 0
        if (heroPoison > 0) {
            heroPoison--
            hero.hp -= 1 + Random.nextInt(2)
            brain.noteReward(-1f)
            if (Random.nextInt(100) < 40) bridge?.log(Narrator.poisonTick(), Tone.BAD)
            if (hero.hp <= 0) {
                bridge?.log(Narrator.poisonEnd(), Tone.BAD)
                die("lingering venom")
                return
            }
        }
        recomputeVision()
        revealNearbyTraps()
        sightTick()
        val stamp = floorStamp
        decide()
        if (heroAlive && floorStamp == stamp) {
            monstersTurn(); npcsTurn(); rivalTick(); familiarTurn(); ambientTick()
        }
        recomputeVision()
        hpMilestones()
        bridge?.onTurnEnd(hero, depth, turns)
    }

    private fun sightTick() {
        for (m in monsters) {
            if (!m.alive || !visible[m.y][m.x]) continue
            if (m.elite) {
                if (!m.introDone) {
                    m.introDone = true
                    bridge?.onDiscoverMonster(m.type.id)
                    bridge?.log(Narrator.eliteIntro(m.eliteName, m.affix!!.tag,
                        m.type.name, m.quirk?.hint ?: ""), Tone.BAD)
                }
            } else if (seenTypes.add(m.type.id)) {
                bridge?.onDiscoverMonster(m.type.id)
                if (m.type.boss) bridge?.log(Narrator.wardenIntro(m.type.name), Tone.BAD)
                else bridge?.log(Narrator.firstSight(m.type, biome), Tone.PLAIN)
            }
        }
        for (n in npcs) {
            if (n.greeted || !visible[n.y][n.x]) continue
            n.greeted = true
            bridge?.onDiscoverNpc(n.kind.name)
            bridge?.log("${n.name} ${n.title}, ${n.description}. ${n.greeting}", Tone.PLAIN)
        }
    }

    private fun ambientTick() {
        if (tauntCd > 0) tauntCd--
        if (ambientCd > 0) { ambientCd--; return }
        if (monsters.none { it.alive && visible[it.y][it.x] } && Random.nextInt(100) < 50) {
            bridge?.log(Narrator.ambient(biome), Tone.PLAIN)
        }
        ambientCd = 45 + Random.nextInt(55)
    }

    private fun hpMilestones() {
        if (!heroAlive) return
        val p = hero.hp * 100 / hero.maxHp
        if (p <= 50 && !said50) { said50 = true; bridge?.log(Narrator.lowHp(0), Tone.BAD) }
        if (p <= 25 && !said25) { said25 = true; bridge?.log(Narrator.lowHp(1), Tone.BAD) }
        if (p <= 12 && !said12) { said12 = true; bridge?.log(Narrator.lowHp(2), Tone.BAD) }
        if (p > 55) { said50 = false; said25 = false; said12 = false }
    }

    /** nothing pressing — safe to slow the heartbeat when nobody is watching */
    fun calm(): Boolean = mood == Mood.DELIBERATE && monsters.none { it.alive && it.aggro }

    // ================= the AI brain =================

    private fun decide() {
        val here = Pt(hero.x, hero.y)
        val dist = walkDistFrom(here)
        val threats = monsters.filter { it.alive && visible[it.y][it.x] }
        val danger = if (threats.isNotEmpty()) dangerMapFrom(threats) else null
        val hpPct = if (hero.maxHp > 0) hero.hp * 100 / hero.maxHp else 100
        val poisoned = heroPoison > 0
        updateMood(hpPct)

        // 1) the spellbook — tactical, always on
        if (tryActives(threats, hpPct, poisoned)) return

        // 2) the one rule survival never delegates: a flask at death's door
        if (hero.potions > 0 && hpPct <= 20) {
            hero.potions--
            runStats["potions"] = (runStats["potions"] ?: 0) + 1
            hero.hp = minOf(hero.maxHp, hero.hp + 12 + hero.pPotion * 3)
            if (poisoned) heroPoison = maxOf(0, heroPoison - 2)
            bridge?.log(Narrator.potion(hero.hp, hero.maxHp), Tone.GOOD)
            return
        }

        // 3) rivals — identity-driven duels stay above strategy
        val hr = rivals.firstOrNull {
            visible[it.y][it.x] &&
            (it.hostile || (personality.focus.huntsRivals && hpPct >= 55))
        }
        if (hr != null) {
            val dR = manhattan(hr.x, hr.y, here.x, here.y)
            if (dR <= 1) { attackRival(hr); return }
            if (hpPct >= 60) { if (stepToward(hr.x, hr.y)) return }
            else {
                var best: Pt? = null; var bestD = dR
                for (dir in DIRS) {
                    val nx = hero.x + dir[0]; val ny = hero.y + dir[1]
                    if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
                    if (map[ny][nx] == Tile.WALL || monsterAt(nx, ny) != null) continue
                    val dd = manhattan(nx, ny, hr.x, hr.y)
                    if (dd > bestD) { bestD = dd; best = Pt(nx, ny) }
                }
                if (best != null) { moveTo(best); return }
            }
        }

        // 3.7) the keeper's toll — sometimes gold buys what steel cannot
        val pact = threats.firstOrNull { it.type.boss && !it.bribed }
        if (pact != null && !bribedThisFloor) {
            val toll = 10 + depth * 4
            val weak = hpPct < 65 || hero.level < depth
            if (weak && hero.gold >= toll &&
                manhattan(pact.x, pact.y, here.x, here.y) <= 3) {
                hero.gold -= toll
                pact.bribed = true; pact.aggro = false
                bribedThisFloor = true
                brain.noteReward(-toll / 30f)
                bridge?.log(Narrator.pactPaid(toll, pact.type.name), Tone.EPIC)
                chron("pact", "paid the ${pact.type.name}'s toll on floor $depth")
                return
            }
        }

        // 4) the strategic turn — a Markov decision
        val panic = (t("panic") +
            (if (poisoned) 15 else 0) +
            (if (threats.any { it.type.boss }) 10 else 0) +
            (if (threats.size >= 2) 5 else 0) + moodPanic() +
            (if (floorMod == FloorMod.THICK) 4 else 0)).toInt()
        if (mdpAct(threats, danger, dist, hpPct, poisoned, panic)) return

        // 5) fallback — the old ladder's safety rails
        if (hpPct < 60 || poisoned) {
            val pot = nearestKnownItem(ItemSlot.POTION, dist)
            if (pot != null && stepToward(pot.x, pot.y)) return
        }
        val dry = hero.potions == 0
        if (dry && (hpPct <= 30 || (poisoned && hpPct <= 45))) {
            if (map[hero.y][hero.x] == Tile.STAIRS) { descend(); return }
            if (stepToStairsSafe(dist)) return
        }
        if (stepToFrontier()) return
        descend()
    }

    // ---------- AI utilities ----------

    private fun walkDistFrom(from: Pt): IntArray {
        val d = IntArray(W * H) { -1 }
        val q = ArrayDeque<Pt>()
        d[key(from.x, from.y)] = 0
        q.add(from)
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            val dp = d[key(p.x, p.y)]
            for (dir in DIRS) {
                val nx = p.x + dir[0]; val ny = p.y + dir[1]
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                if (map[ny][nx] == Tile.WALL) continue
                if (d[key(nx, ny)] >= 0) continue
                d[key(nx, ny)] = dp + 1
                q.add(Pt(nx, ny))
            }
        }
        return d
    }

    private fun dangerMapFrom(threats: List<Monster>): IntArray {
        val d = IntArray(W * H) { Int.MAX_VALUE }
        val q = ArrayDeque<Pt>()
        for (m in threats) {
            val k = key(m.x, m.y)
            if (d[k] != 0) { d[k] = 0; q.add(Pt(m.x, m.y)) }
        }
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            val dp = d[key(p.x, p.y)]
            for (dir in DIRS) {
                val nx = p.x + dir[0]; val ny = p.y + dir[1]
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                if (map[ny][nx] == Tile.WALL) continue
                if (d[key(nx, ny)] <= dp + 1) continue
                d[key(nx, ny)] = dp + 1
                q.add(Pt(nx, ny))
            }
        }
        return d
    }

    private fun monsterThreat(m: Monster): Int {
        var t = m.effectiveAtk() * 3 + m.maxHp / 3
        if (m.type.boss) t += 10
        if (m.elite) t += 6
        if (m.quirk == Quirk.VENOMOUS || m.quirk == Quirk.THIEF) t += 3
        if (m.affix == Affix.SWIFT) t += 4
        return (t * lore.threat(m.type.id)).toInt()
    }

    private fun chooseTarget(threats: List<Monster>, dist: IntArray): Monster? {
        if (threats.isEmpty()) return null
        val expected = (hero.effAtk * 1.2f).toInt().coerceAtLeast(2)
        var best: Monster? = null
        var bestScore = Int.MIN_VALUE
        for (m in threats) {
            val d = maxOf(1, dist[key(m.x, m.y)].let { if (it < 0) 99 else it })
            var score = (m.xpDrop() * 100) / d
            if (m.hp <= expected * 2) score += t("finish").toInt()
            if (m.quirk == Quirk.VENOMOUS || m.quirk == Quirk.THIEF) score += 40
            if (m.affix == Affix.SWIFT) score += 30
            if (m.elite) score += t("elite").toInt()
            if (m.type.boss) score += t("boss").toInt()
            if (m.bounty > 0) score += m.bounty / 2
            if (score > bestScore) { bestScore = score; best = m }
        }
        return best
    }

    private data class Forecast(val win: Boolean, val hpLost: Int)

    private fun forecast(target: Monster, threats: List<Monster>): Forecast {
        var myHp = hero.hp
        var tHp = target.hp
        val crit = 0.10f + hero.pCrit / 100f
        val chip = threats.filter { it !== target &&
            manhattan(it.x, it.y, target.x, target.y) <= 4 }
            .sumOf { maxOf(1, it.effectiveAtk() - hero.effDef) * 6 / 10 }
        var rounds = 0
        while (rounds < 14 && myHp > 0 && tHp > 0) {
            rounds++
            var hDmg = maxOf(1, hero.effAtk + Random.nextInt(-1, 2) - target.effectiveDef())
            if (Random.nextFloat() < crit) hDmg *= 2
            tHp -= hDmg
            if (tHp <= 0) break
            var mDmg = maxOf(1, target.effectiveAtk() + Random.nextInt(-1, 2) - hero.effDef)
            mDmg += maxOf(0, chip)
            myHp -= mDmg
        }
        return Forecast(tHp <= 0, maxOf(0, hero.hp - myHp))
    }

    private fun assessFight(target: Monster, threats: List<Monster>, hpPct: Int): Verdict {
        if (freeNeighbors() <= 1) {
            verdictReason = "cornered — nothing left but teeth"
            return Verdict.ENGAGE
        }
        val fc = forecast(target, threats)
        val reward = target.xpDrop() + target.goldDrop() / 2
        val boss = target.type.boss
        val brave = t("aggression") * personality.focus.fightMul

        if (!fc.win) {
            val clutch = hero.potions >= 1 && hpPct >= 45 && (boss || target.elite)
            verdictReason = if (clutch) "a bad fight, but the flask makes it possible"
                            else "the forecast says she loses this exchange"
            return if (clutch) Verdict.ENGAGE
                   else if (hpPct >= 70) Verdict.KITE else Verdict.RETREAT
        }
        if (boss) {
            val fear = t("boss")
            if (fc.hpLost * (1f + fear / 120f) > hero.hp * 0.55f && hpPct < 75) {
                verdictReason = "the keeper outmatches this life — for now"
                return if (hero.potions > 0 && hpPct >= 55) Verdict.ENGAGE else Verdict.RETREAT
            }
        }
        if (!boss && fc.hpLost * 3 > reward * t("grit") && hpPct < 85) {
            verdictReason = "this fight costs more blood than it pays"
            return if (hpPct >= 75) Verdict.KITE else Verdict.RETREAT
        }
        if (hpPct <= 25 && fc.hpLost >= hero.hp) {
            verdictReason = "one more exchange kills her"
            return Verdict.RETREAT
        }
        if (threats.size >= 2 && hpPct < 60) {
            verdictReason = "two blades, one hero — trade from range"
            return Verdict.KITE
        }
        verdictReason = null
        return Verdict.ENGAGE
    }

    private enum class Verdict { ENGAGE, KITE, RETREAT }

    private fun freeNeighbors(): Int {
        var n = 0
        for (dir in DIRS) {
            val nx = hero.x + dir[0]; val ny = hero.y + dir[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] != Tile.WALL && monsterAt(nx, ny) == null) n++
        }
        return n
    }

    private fun muse() {
        val r = verdictReason ?: return
        verdictReason = null
        if (Random.nextInt(100) < 30) bridge?.log(Narrator.forethought(r), Tone.PLAIN)
    }

    private fun updateMood(hpPct: Int) {
        val next = when {
            hpPct <= 25 -> Mood.WOUNDED
            hpPct <= 50 && monsters.any { it.alive && it.aggro } -> Mood.HUNTED
            killStreak >= 4 -> Mood.BLOODIED
            turnsThisFloor > 90 -> Mood.WEARY
            else -> Mood.DELIBERATE
        }
        if (next != mood) {
            mood = next
            if (next != Mood.DELIBERATE && Random.nextInt(100) < 45)
                bridge?.log(Narrator.moodLine(next.name), Tone.PLAIN)
        }
    }

    private fun moodPanic(): Int = when (mood) {
        Mood.WOUNDED -> 10
        Mood.HUNTED -> 5
        else -> 0
    }

    // ---------- the tactical tree ----------

    private fun planTactical(threats: List<Monster>, goal: TactMCTS.Goal): Pt? {
        val near = threats.filter { manhattan(it.x, it.y, hero.x, hero.y) <= 10 }
            .sortedBy { manhattan(it.x, it.y, hero.x, hero.y) }.take(8)
        if (near.isEmpty()) return null
        val loot = ArrayList<Triple<Int, Int, Int>>()
        for (fl in items) {
            if (!knownItems.contains(key(fl.x, fl.y))) continue
            if (manhattan(fl.x, fl.y, hero.x, hero.y) > 8) continue
            loot.add(Triple(fl.x, fl.y,
                if (fl.item.slot == ItemSlot.GOLD) (fl.amount / 2).coerceAtLeast(4) else 18))
            if (loot.size >= 4) break
        }
        val plan = TactMCTS.Plan(
            hero.x, hero.y, hero.hp, hero.effAtk, hero.effDef, hero.pThorns,
            10 + hero.pCrit, near.map {
                TactMCTS.Foe(it.x, it.y, it.hp, it.effectiveAtk(), monsterThreat(it),
                             it.quirk == Quirk.COWARD, it.affix == Affix.SWIFT)
            }, loot, goal, stairs.x, stairs.y,
            if (goal == TactMCTS.Goal.FLEE) 0.30f else 0.12f,
            { x, y -> x in 1 until W - 1 && y in 1 until H - 1 && heroPassable(x, y) },
            { x, y -> x in 1 until W - 1 && y in 1 until H - 1 && map[y][x] != Tile.WALL }
        )
        val a = mcts.search(plan, mctsBudget)
        if (mcts.plans % 250 == 0 && mcts.plans > 0)
            bridge?.log(Narrator.mctsMilestone(mcts.plans, mcts.overrides), Tone.PLAIN)
        if (a <= 0) return null
        return Pt(hero.x + MDX[a], hero.y + MDY[a])
    }

    private fun engage(target: Monster, threats: List<Monster>, dist: IntArray) {
        val planned = planTactical(threats, TactMCTS.Goal.HUNT)
        if (planned != null) {
            val naive = bfsFirstStep({ x, y -> x == target.x && y == target.y },
                                     { x, y -> heroPassable(x, y) })
            if (naive != null && (naive.x != planned.x || naive.y != planned.y)) {
                mcts.overrides++
                if (Random.nextInt(100) < 15)
                    bridge?.log(Narrator.mctsAhead(target.type.name), Tone.PLAIN)
            }
            moveTo(planned)
            return
        }
        if (manhattan(target.x, target.y, hero.x, hero.y) > 1) {
            if (threats.size >= 2) isolateStep(threats)?.let { moveTo(it); return }
            if (monsters.count { it.alive && manhattan(it.x, it.y, hero.x, hero.y) <= 2 } >= 2) {
                chokepointStep()?.let { moveTo(it); return }
            }
        }
        if (manhattan(target.x, target.y, hero.x, hero.y) <= 1) { heroAttack(target); return }
        if (!stepToward(target.x, target.y)) adjacentMonster()?.let { heroAttack(it) }
    }

    private fun isolateStep(group: List<Monster>): Pt? {
        val here = group.count { manhattan(it.x, it.y, hero.x, hero.y) <= 3 }
        var best: Pt? = null
        var bestCount = here
        for (dir in DIRS) {
            val nx = hero.x + dir[0]; val ny = hero.y + dir[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (!heroPassable(nx, ny)) continue
            val c = group.count { manhattan(it.x, it.y, nx, ny) <= 3 }
            if (c < bestCount) { bestCount = c; best = Pt(nx, ny) }
        }
        return best
    }

    private fun chokepointStep(): Pt? {
        var best: Pt? = null
        var bestOpen = 9
        for (dir in DIRS) {
            val nx = hero.x + dir[0]; val ny = hero.y + dir[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] == Tile.WALL || monsterAt(nx, ny) != null) continue
            if (map[ny][nx] == Tile.STAIRS) continue
            var open = 0
            for (e in DIRS) {
                val ex = nx + e[0]; val ey = ny + e[1]
                if (ex in 1 until W - 1 && ey in 1 until H - 1 &&
                    map[ey][ex] != Tile.WALL && monsterAt(ex, ey) == null) open++
            }
            if (open < bestOpen) { bestOpen = open; best = Pt(nx, ny) }
        }
        return if (bestOpen <= 2) best else null
    }

    private fun kite(target: Monster, threats: List<Monster>): Boolean {
        val ranged = actives.firstOrNull {
            ready(it) && it.fx in listOf(SkillFx.DMG_RANGED, SkillFx.DMG_ALL, SkillFx.DMG_SINGLE)
        }
        val gap = manhattan(target.x, target.y, hero.x, hero.y)
        if (ranged != null && gap >= 2) return useSkill(ranged)
        if (gap <= 1) { heroAttack(target); if (!heroAlive) return true }
        return retreat(threats, dangerMapFrom(threats), false)
    }

    private fun retreat(threats: List<Monster>, danger: IntArray?, towardStairs: Boolean): Boolean {
        val planned = planTactical(threats, TactMCTS.Goal.FLEE)
        if (planned != null) {
            monsterAt(planned.x, planned.y)?.let { f ->
                mcts.overrides++
                if (Random.nextInt(100) < 30)
                    bridge?.log(Narrator.mctsEscape(f.type.name), Tone.PLAIN)
            }
            moveTo(planned)
            return true
        }
        val safe = danger ?: dangerMapFrom(threats)
        var best: Pt? = null
        var bestScore = Int.MIN_VALUE
        for (dir in DIRS) {
            val nx = hero.x + dir[0]; val ny = hero.y + dir[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] == Tile.WALL || monsterAt(nx, ny) != null) continue
            val dv = safe[key(nx, ny)].let { if (it == Int.MAX_VALUE) 30 else it }
            var score = dv * 10 + Random.nextInt(0, 8)
            if (towardStairs) score += (20 - manhattan(nx, ny, stairs.x, stairs.y)) * 2
            if (score > bestScore) { bestScore = score; best = Pt(nx, ny) }
        }
        if (best == null) {
            adjacentMonster()?.let { heroAttack(it); return true }
            return false
        }
        moveTo(best)
        return true
    }

    private fun stepToStairsSafe(dist: IntArray): Boolean {
        val boss = monsters.firstOrNull { it.alive && it.type.boss && !it.bribed }
        if (boss != null && (hero.hp * 100 / hero.maxHp < 60 || hero.level < depth)) {
            val step = bfsFirstStep(
                goal = { x, y -> x == stairs.x && y == stairs.y },
                passable = { x, y ->
                    heroPassable(x, y) &&
                    manhattan(x, y, boss.x, boss.y) > t("skirt").toInt()
                })
            if (step != null) { moveTo(step); return true }
        }
        return stepToward(stairs.x, stairs.y)
    }

    private fun heroPassable(x: Int, y: Int): Boolean {
        if (x <= 0 || y <= 0 || x >= W - 1 || y >= H - 1) return false
        if (map[y][x] == Tile.WALL || monsterAt(x, y) != null) return false
        val f = featAt(x, y) ?: return true
        if (f.revealed && f.kind in listOf(FeatKind.SPIKES, FeatKind.GAS, FeatKind.GLYPH)) return false
        if (f.kind == FeatKind.LOCKED && ignoredFeats.contains(f)) return false
        return true
    }

    // ---------- the strategic MDP ----------

    private fun mdpAct(
        threats: List<Monster>, danger: IntArray?, dist: IntArray,
        hpPct: Int, poisoned: Boolean, panic: Int
    ): Boolean {
        val threatSum = threats.sumOf { monsterThreat(it) }
        val myPower = hero.effAtk * 2 + hero.effDef * 2 + hero.hp / 3
        val dangerLvl = when {
            threats.isEmpty() -> 0
            threatSum > myPower * 2 -> 3
            threats.size >= 2 -> 2
            else -> 1
        }
        val exp = exploredFraction()
        val onStairs = map[hero.y][hero.x] == Tile.STAIRS
        val rich = hero.gold >= t("reserve") * 2 + depth * 4
        val s = mdp.encode(hpPct, dangerLvl, hero.potions, exp, onStairs, rich, poisoned)

        val candidates = buildCandidates(threats, dist, hpPct, poisoned, panic, exp, onStairs)
        if (lastMdpAction >= 0 && lastMdpState >= 0)
            mdp.learn(lastMdpState, lastMdpAction, s, candidates.map { it.first })
        if (candidates.isEmpty()) { lastMdpAction = -1; return false }

        val chosen = mdp.choose(s, candidates)
        lastMdpState = s
        lastMdpAction = chosen
        if (mdp.decisions > 0 && mdp.decisions % 500 == 0)
            bridge?.log(Narrator.mdpMilestone(mdp.decisions, mdp.size()), Tone.PLAIN)
        if (mdp.lastOverrode && Random.nextInt(100) < 25)
            bridge?.log(Narrator.mdpOverride(actionName(chosen)), Tone.PLAIN)
        return execMdp(chosen, threats, danger, dist, hpPct, poisoned, exp)
    }

    private fun actionName(a: Int): String = when (a) {
        HeroMDP.FIGHT -> "the melee"; HeroMDP.WITHDRAW -> "withdrawal"
        HeroMDP.AID -> "finding aid"; HeroMDP.LOOT -> "the shine"
        HeroMDP.NPC -> "the trade"; HeroMDP.DESCEND -> "the stairs"
        HeroMDP.POTION -> "the flask"
        else -> "the unexplored"
    }

    private fun buildCandidates(
        threats: List<Monster>, dist: IntArray, hpPct: Int,
        poisoned: Boolean, panic: Int, exp: Float, onStairs: Boolean
    ): List<Pair<Int, Float>> {
        val out = ArrayList<Pair<Int, Float>>()

        if (threats.isNotEmpty()) {
            val target = chooseTarget(threats, dist)
            if (target != null) {
                when (assessFight(target, threats, hpPct)) {
                    Verdict.ENGAGE  -> { out.add(HeroMDP.FIGHT to 3f); out.add(HeroMDP.WITHDRAW to 0.4f) }
                    Verdict.KITE    -> { out.add(HeroMDP.WITHDRAW to 2.2f); out.add(HeroMDP.FIGHT to 1.6f) }
                    Verdict.RETREAT -> out.add(HeroMDP.WITHDRAW to 3f)
                }
            }
        }

        if (hero.potions > 0 && hpPct <= panic)
            out.add(HeroMDP.POTION to 2.2f + (panic - hpPct) / 8f)

        val aidPrior = when {
            poisoned && hpPct < 70 -> 2.6f
            hpPct < 50 -> 2.4f
            hpPct < 65 -> 1.2f
            else -> 0f
        }
        if (aidPrior > 0 && (nearestKnownItem(ItemSlot.POTION, dist) != null ||
            npcs.any { it.kind == NpcKind.HEALER && explored[it.y][it.x] && !npcMemory.contains(it) }))
            out.add(HeroMDP.AID to aidPrior)

        if (worthwhileLoot(dist))
            out.add(HeroMDP.LOOT to if (lootTarget(dist) != null) 3.2f else 1.1f)

        if (findNpcGoal() != null) out.add(HeroMDP.NPC to 1f)

        val need = descentNeed(hpPct, poisoned)
        val dry = hero.potions == 0
        val bailout = dry && (hpPct <= 30 || (poisoned && hpPct <= 45))
        out.add(HeroMDP.DESCEND to when {
            bailout -> 4f
            onStairs && exp >= need -> 2.8f
            else -> t("stairBias") + (exp - need) * 4f
        })

        if (hasFrontier()) out.add(HeroMDP.EXPLORE to 1f)
        return out
    }

    private fun execMdp(
        a: Int, threats: List<Monster>, danger: IntArray?, dist: IntArray,
        hpPct: Int, poisoned: Boolean, exp: Float
    ): Boolean = when (a) {
        HeroMDP.FIGHT -> {
            val target = chooseTarget(threats, dist) ?: return false
            engage(target, threats, dist)
            true
        }
        HeroMDP.WITHDRAW -> {
            if (chooseTarget(threats, dist) != null) muse()
            retreat(threats, danger, false)
        }
        HeroMDP.POTION -> {
            hero.potions--
            runStats["potions"] = (runStats["potions"] ?: 0) + 1
            hero.hp = minOf(hero.maxHp, hero.hp + 12 + hero.pPotion * 3)
            if (poisoned) heroPoison = maxOf(0, heroPoison - 2)
            bridge?.log(Narrator.potion(hero.hp, hero.maxHp), Tone.GOOD)
            true
        }
        HeroMDP.AID -> {
            val pot = nearestKnownItem(ItemSlot.POTION, dist)
            if (pot != null && stepToward(pot.x, pot.y)) true
            else npcs.firstOrNull { it.kind == NpcKind.HEALER && explored[it.y][it.x] &&
                !npcMemory.contains(it) }?.let { stepToward(it.x, it.y) } ?: false
        }
        HeroMDP.LOOT -> seekLoot(dist) || seekFeats(dist)
        HeroMDP.NPC -> npcEconomy(dist)
        HeroMDP.DESCEND -> {
            if (map[hero.y][hero.x] == Tile.STAIRS && exp >= descentNeed(hpPct, poisoned)) {
                descend(); true
            } else stepToStairsSafe(dist)
        }
        HeroMDP.EXPLORE -> stepToFrontier()
        else -> false
    }

    private fun descentNeed(hpPct: Int, poisoned: Boolean): Float =
        ((if (poisoned || hpPct < 40) t("descent") - 0.15f else t("descent"))
            * personality.focus.exploreMul).coerceIn(0.28f, 0.75f) -
            (if (mood == Mood.WEARY) 0.06f else 0f)

    private fun hasFrontier(): Boolean {
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (!explored[y][x] || map[y][x] == Tile.WALL) continue
            if (isFrontier(x, y)) return true
        }
        return false
    }

    private fun lootTarget(dist: IntArray): FloorItem? =
        items.firstOrNull { knownItems.contains(key(it.x, it.y)) &&
            it.item.rarity >= Rarity.LEGENDARY }

    private fun worthwhileLoot(dist: IntArray): Boolean {
        if (lootTarget(dist) != null) return true
        for (fl in items) {
            if (!knownItems.contains(key(fl.x, fl.y))) continue
            if (lootValue(fl) >= 20) return true
        }
        return feats.any { f -> !f.used && !ignoredFeats.contains(f) && explored[f.y][f.x] &&
            f.kind in listOf(FeatKind.CHEST, FeatKind.SHRINE) }
    }

    // ---------- pathfinding ----------

    private fun stepToward(tx: Int, ty: Int): Boolean {
        if (tx == hero.x && ty == hero.y) return false
        val step = bfsFirstStep(
            goal = { x, y -> x == tx && y == ty },
            passable = { x, y -> heroPassable(x, y) }
        ) ?: return false
        moveTo(step)
        return true
    }

    private fun stepToFrontier(): Boolean {
        val step = bfsFirstStep(
            goal = { x, y -> map[y][x] != Tile.WALL && isFrontier(x, y) },
            passable = { x, y -> heroPassable(x, y) }
        ) ?: return false
        moveTo(step)
        return true
    }

    private fun bfsFirstStep(goal: (Int, Int) -> Boolean, passable: (Int, Int) -> Boolean): Pt? {
        if (goal(hero.x, hero.y)) return null
        val startK = key(hero.x, hero.y)
        val parent = HashMap<Int, Int>()
        parent[startK] = -1
        val queue = ArrayDeque<Pt>()
        queue.add(Pt(hero.x, hero.y))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (d in DIRS) {
                val nx = p.x + d[0]; val ny = p.y + d[1]
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
                val nk = key(nx, ny)
                if (parent.containsKey(nk)) continue
                if (goal(nx, ny)) {
                    if (p.x == hero.x && p.y == hero.y) return Pt(nx, ny)
                    var cur = p
                    while (parent[key(cur.x, cur.y)] != startK)
                        cur = decode(parent[key(cur.x, cur.y)]!!)
                    return cur
                }
                if (passable(nx, ny)) { parent[nk] = key(p.x, p.y); queue.add(Pt(nx, ny)) }
            }
        }
        return null
    }

    private fun isFrontier(x: Int, y: Int): Boolean {
        for (d in DIRS) {
            val nx = x + d[0]; val ny = y + d[1]
            if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
            if (!explored[ny][nx]) return true
        }
        return false
    }

    // ---------- the spellbook ----------

    private fun ready(s: Skill) = (hero.cooldowns[s.id] ?: 0) <= 0
    private val actives get() = hero.skills.filter { it.kind == SkillKind.ACTIVE }

    private fun skillTarget(): Monster? {
        val vis = monsters.filter { it.alive && visible[it.y][it.x] }
        if (vis.isEmpty()) return null
        vis.firstOrNull { it.hp <= 8 + hero.level && !it.type.boss }?.let { return it }
        return vis.maxByOrNull { monsterThreat(it) * 2 + (it.maxHp - it.hp) }
    }

    private fun tryActives(threats: List<Monster>, hpPct: Int, poisoned: Boolean): Boolean {
        val healGate = (if (poisoned) t("healGate") + 15 else t("healGate")) +
            (if (mood == Mood.WOUNDED) 10 else 0)
        actives.firstOrNull { it.fx == SkillFx.HEAL && ready(it) && hpPct <= healGate }?.let {
            useSkill(it); return true
        }
        if (hpPct <= 35 && threats.any { manhattan(it.x, it.y, hero.x, hero.y) <= 1 }) {
            actives.firstOrNull { it.fx == SkillFx.BLINK && ready(it) }?.let {
                if (useSkill(it)) return true
            }
        }
        if (hero.potions == 0 && hpPct <= 70) {
            actives.firstOrNull { it.fx == SkillFx.POTION && ready(it) }?.let {
                useSkill(it); return true
            }
        }
        if (threats.isEmpty()) return false

        val boss = threats.any { it.type.boss }
        if (threats.size >= 2 || boss) {
            actives.firstOrNull { it.fx == SkillFx.BUFF_ATK && ready(it) }?.let {
                useSkill(it); return true
            }
            actives.firstOrNull { it.fx == SkillFx.BUFF_DEF && ready(it) }?.let {
                useSkill(it); return true
            }
        }
        if (boss || hpPct <= 50 || threats.size >= 2) {
            actives.firstOrNull { it.fx == SkillFx.STUN && ready(it) }?.let {
                useSkill(it); return true
            }
        }
        val near = threats.count { manhattan(it.x, it.y, hero.x, hero.y) <= 2 }
        if (near >= 2) {
            actives.firstOrNull { it.fx == SkillFx.DMG_ALL && ready(it) }?.let {
                useSkill(it); return true
            }
        }
        actives.firstOrNull { (it.fx == SkillFx.DMG_SINGLE || it.fx == SkillFx.DMG_RANGED) && ready(it) }?.let { s ->
            val juicy = threats.any { it.maxHp >= 10 || it.type.boss || it.elite }
            if (juicy) { useSkill(s); return true }
            if (s.tier <= 1 && Random.nextInt(100) <
                (1f - t("nukeSave")) * 100 * t("skillBias")) { useSkill(s); return true }
        }
        return false
    }

    private fun useSkill(s: Skill): Boolean {
        hero.cooldowns[s.id] = s.cd
        runStats["skills"] = (runStats["skills"] ?: 0) + 1
        when (s.fx) {
            SkillFx.HEAL -> {
                hero.hp = minOf(hero.maxHp, hero.hp + s.power)
                bridge?.log("${Skills.emoji(s.school)} " + Narrator.skillSelf(s.name, s.fx, s.power, s.dur), Tone.GOOD)
            }
            SkillFx.BUFF_ATK -> {
                hero.buffAtk = s.power; hero.buffAtkT = s.dur
                bridge?.log("${Skills.emoji(s.school)} " + Narrator.skillSelf(s.name, s.fx, s.power, s.dur), Tone.EPIC)
            }
            SkillFx.BUFF_DEF -> {
                hero.buffDef = s.power; hero.buffDefT = s.dur
                bridge?.log("${Skills.emoji(s.school)} " + Narrator.skillSelf(s.name, s.fx, s.power, s.dur), Tone.EPIC)
            }
            SkillFx.BLINK -> {
                if (!blink()) { hero.cooldowns.remove(s.id); return false }
                bridge?.log("${Skills.emoji(s.school)} " + Narrator.skillSelf(s.name, s.fx, 0, 0), Tone.EPIC)
            }
            SkillFx.POTION -> {
                hero.potions += s.power
                bridge?.log("${Skills.emoji(s.school)} " + Narrator.skillSelf(s.name, s.fx, s.power, 0), Tone.GOOD)
            }
            SkillFx.STUN -> {
                val t = skillTarget() ?: return false
                val dmg = skillDamage(s, t)
                t.hp -= dmg; t.stun = 3
                bridge?.log("${Skills.emoji(s.school)} " + Narrator.skillStun(s.name, t.type.name), Tone.GOOD)
                if (t.hp <= 0) killMonster(t, "the ${s.name}")
            }
            SkillFx.DMG_ALL -> {
                val targets = monsters.filter { it.alive && visible[it.y][it.x] }
                if (targets.isEmpty()) { hero.cooldowns.remove(s.id); return false }
                bridge?.log("${Skills.emoji(s.school)} " +
                    Narrator.skillCast(s.school, s.name, "everything in the light", s.power), Tone.GOOD)
                for (t in targets.toList()) { t.hp -= skillDamage(s, t); if (t.hp <= 0) killMonster(t, "the ${s.name}") }
            }
            SkillFx.DMG_SINGLE, SkillFx.DMG_RANGED -> {
                val t = skillTarget() ?: return false
                val dmg = skillDamage(s, t)
                t.hp -= dmg
                bridge?.log("${Skills.emoji(s.school)} " +
                    Narrator.skillCast(s.school, s.name, t.type.name, dmg), Tone.GOOD)
                if (t.hp <= 0) killMonster(t, "the ${s.name}")
            }
            else -> {}
        }
        return true
    }

    private fun skillDamage(s: Skill, t: Monster): Int =
        maxOf(1, s.power + hero.level / 2 + Random.nextInt(-1, 2) - t.effectiveDef() / 2)

    private fun blink(): Boolean {
        val candidates = ArrayList<Pt>()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            if (map[y][x] == Tile.WALL) continue
            val d = manhattan(x, y, hero.x, hero.y)
            if (d < 5 || d > 13) continue
            val threatened = monsters.any { it.alive && manhattan(it.x, it.y, x, y) <= 1 }
            if (threatened) continue
            candidates.add(Pt(x, y))
        }
        if (candidates.isEmpty()) return false
        val p = if (hero.hp * 100 / hero.maxHp < 40)
            candidates.minByOrNull { manhattan(it.x, it.y, stairs.x, stairs.y) }!!
        else candidates.random()
        hero.x = p.x; hero.y = p.y
        return true
    }

    // ---------- skill learning ----------

    private fun learnSkill(s: Skill, verb: String = "learns") {
        if (hero.skills.any { it.id == s.id }) return
        if (s.kind == SkillKind.ACTIVE) {
            val current = hero.skills.filter { it.kind == SkillKind.ACTIVE }
            if (current.size >= activeSlots) {
                val worst = current.minByOrNull { it.tier }!!
                if (s.tier >= worst.tier) {
                    hero.skills.remove(worst)
                    hero.cooldowns.remove(worst.id)
                    hero.skills.add(s)
                    bridge?.log("${Skills.emoji(s.school)} swaps out ${worst.name} — the art of ${s.name} takes its place", Tone.EPIC)
                } else {
                    hero.gold += 8
                    bridge?.log("the art of ${s.name} won't settle — sells the scroll for 8 gold", Tone.PLAIN)
                }
                return
            }
        }
        hero.skills.add(s)
        hero.atk += s.pAtk; hero.def += s.pDef
        if (s.pHp > 0) { hero.maxHp += s.pHp; hero.hp += s.pHp }
        hero.pCrit += s.pCrit; hero.pThorns += s.pThorns; hero.pLifesteal += s.pLifesteal
        hero.pVision += s.pVision; hero.pPotion += s.pPotion
        hero.pGold += s.pGold; hero.pXp += s.pXp
        chron("skill", "learned ${s.name}")
        if (verb != "carries")
            bridge?.log("${Skills.emoji(s.school)} $verb ${Narrator.learnSkill(s.name)}", Tone.EPIC)
        else
            bridge?.log("${Skills.emoji(s.school)} carries the art of ${s.name}", Tone.PLAIN)
    }

    private fun grantLearned(tiers: List<Int>, verb: String) {
        val known = hero.skills.map { it.id }.toSet()
        val skill = if (depth >= 5 && Random.nextInt(100) < 25)
            SkillForge.forge(biome.school, depth)
        else Skills.randomTier(tiers, known)
        if (skill != null) learnSkill(skill, verb)
    }

    // ---------- economy ----------

    private fun smithCost(n: Npc): Int {
        var c = ((12 + depth * 2) * n.temper.priceMult).toInt().coerceAtLeast(5)
        if (n.quirk == NpcQuirk.HAGGLER) c = (c * 0.75f).toInt().coerceAtLeast(3)
        return c
    }
    private fun mapCost(n: Npc): Int {
        var c = ((10 + depth * 2) * n.temper.priceMult).toInt().coerceAtLeast(5)
        if (n.quirk == NpcQuirk.HAGGLER) c = (c * 0.75f).toInt().coerceAtLeast(3)
        return c
    }
    private fun madPrice(base: Int): Int = when (Random.nextInt(3)) {
        0 -> 0; 1 -> base; else -> base * 2
    }

    private fun findNpcGoal(): Npc? {
        fun known(n: Npc) = explored[n.y][n.x]
        fun fresh(n: Npc) = !npcMemory.contains(n)
        val reserve = t("reserve").toInt() + depth * 2
        npcs.firstOrNull { it.kind == NpcKind.HEALER && fresh(it) && known(it) &&
            hero.hp <= hero.maxHp * 60 / 100 }?.let { return it }
        npcs.firstOrNull { it.kind == NpcKind.SMITH && fresh(it) && known(it) &&
            hero.gold >= smithCost(it) + reserve / 2 }?.let { return it }
        npcs.firstOrNull { it.kind == NpcKind.MAPKEEPER && fresh(it) && known(it) &&
            hero.gold >= mapCost(it) + reserve / 2 && exploredFraction() < 0.5f }
            ?.let { return it }
        npcs.firstOrNull { it.kind == NpcKind.MERCHANT && fresh(it) && known(it) &&
            it.offer != null && hero.gold >= it.offer!!.price + reserve / 2 &&
            offerWorth(it.offer!!) }?.let { return it }
        npcs.firstOrNull { it.kind == NpcKind.LORE && fresh(it) && known(it) }?.let { return it }
        npcs.firstOrNull { it.kind == NpcKind.GAMBLER && fresh(it) && known(it) &&
            hero.gold >= reserve + 60 && Random.nextInt(100) < 25 }?.let { return it }
        return npcs.filter { it.kind.oneTime && known(it) &&
                manhattan(it.x, it.y, hero.x, hero.y) <= 8 }
            .minByOrNull { manhattan(it.x, it.y, hero.x, hero.y) }
    }

    private fun npcEconomy(dist: IntArray): Boolean {
        val n = findNpcGoal() ?: return false
        return stepToward(n.x, n.y)
    }

    private fun offerWorth(o: Offer): Boolean = when (o.item.slot) {
        ItemSlot.POTION  -> hero.potions < 5
        ItemSlot.WEAPON  -> hero.weapon == null || o.item.equipScore > hero.weapon!!.equipScore
        ItemSlot.ARMOR   -> hero.armor == null || o.item.equipScore > hero.armor!!.equipScore
        ItemSlot.TRINKET -> hero.trinket == null || o.item.equipScore > hero.trinket!!.equipScore
        else -> true
    }

    // ---------- loot valuation ----------

    private fun seekLoot(dist: IntArray): Boolean {
        var best: FloorItem? = null
        var bestScore = 0
        for (fl in items) {
            if (!knownItems.contains(key(fl.x, fl.y))) continue
            if (fl.item.rarity >= Rarity.LEGENDARY) { best = fl; break }
            val d = maxOf(2, dist[key(fl.x, fl.y)].let { if (it < 0) 99 else it })
            val score = (lootValue(fl) * t("greed") * personality.focus.lootMul).toInt() / d
            if (score > bestScore) { bestScore = score; best = fl }
        }
        best?.let { return stepToward(it.x, it.y) }
        return false
    }

    private fun seekFeats(dist: IntArray): Boolean {
        var best: FloorFeat? = null; var bestScore = 0
        for (f in feats) {
            if (f.used || ignoredFeats.contains(f) || !explored[f.y][f.x]) continue
            val d = maxOf(2, dist[key(f.x, f.y)].let { if (it < 0) 99 else it })
            val v = when (f.kind) {
                FeatKind.CHEST -> 90
                FeatKind.LOCKED -> if (hero.hp > hero.maxHp * 45 / 100) 70 else 0
                FeatKind.SHRINE -> if (hero.hp < hero.maxHp * 80 / 100) 65 else 40
                else -> 0
            }
            if (v <= 0) continue
            val score = (v * t("greed").toInt() * personality.focus.lootMul).toInt() / d
            if (score > bestScore) { bestScore = score; best = f }
        }
        best?.let { return stepToward(it.x, it.y) }
        return false
    }

    private fun lootValue(fl: FloorItem): Int {
        val item = fl.item
        if (item.slot == ItemSlot.GOLD) return fl.amount
        fun gain(cur: Item?): Int = item.equipScore - (cur?.equipScore ?: 0)
        return when (item.slot) {
            ItemSlot.POTION -> (if (hero.potions < 4) 40 else 12) + item.heal / 2
            ItemSlot.RELIC  -> 60 + item.equipScore
            ItemSlot.WEAPON -> {
                val cur = hero.weapon
                if (cur == null || item.equipScore > cur.equipScore) 30 + gain(cur) * 4
                else maxOf(1, item.price / 2 - 2)
            }
            ItemSlot.ARMOR -> {
                val cur = hero.armor
                if (cur == null || item.equipScore > cur.equipScore) 30 + gain(cur) * 4
                else maxOf(1, item.price / 2 - 2)
            }
            else -> {
                val cur = hero.trinket
                if (cur == null || item.equipScore > cur.equipScore) 25 + gain(cur) * 4
                else maxOf(1, item.price / 2 - 2)
            }
        }
    }

    // ---------- actions ----------

    private fun moveTo(p: Pt) {
        val npc = npcAt(p.x, p.y)
        if (npc != null) { interact(npc); if (!npc.kind.oneTime) npcSidestep(npc); return }
        val rv = rivalAt(p.x, p.y)
        if (rv != null) { attackRival(rv); return }
        val m = monsterAt(p.x, p.y)
        if (m != null) { heroAttack(m); return }
        hero.x = p.x; hero.y = p.y
        featAt(p.x, p.y)?.let { triggerFeat(it) }
        if (!heroAlive) return
        val itm = items.firstOrNull { it.x == p.x && it.y == p.y }
        if (itm != null) pickUp(itm)
    }

    private fun pickUp(fl: FloorItem) {
        items.remove(fl)
        knownItems.remove(key(fl.x, fl.y))
        when (fl.item.slot) {
            ItemSlot.GOLD -> {
                val g = fl.amount + hero.pGold
                hero.gold += g
                brain.noteReward(fl.amount / 25f)
                bridge?.log("collects $g gold", Tone.PLAIN)
            }
            else -> applyItem(fl.item)
        }
    }

    private fun applyItem(item: Item) {
        bridge?.onDiscoverItem(item.name)
        when (item.slot) {
            ItemSlot.POTION -> applyPotion(item)
            ItemSlot.RELIC -> applyRelic(item)
            else -> equipOrSalvage(item)
        }
    }

    private fun applyPotion(p: Item) {
        brain.noteReward(5f + p.heal / 4f)
        hero.potions += p.potions
        if (p.heal > 0) hero.hp = minOf(hero.maxHp, hero.hp + p.heal)
        if (p.hp > 0) { hero.maxHp += p.hp; hero.hp += p.hp }
        if (p.potionPower > 0) hero.pPotion += p.potionPower
        bridge?.log(Narrator.itemPickup(p),
            if (p.rarity >= Rarity.RARE) Tone.EPIC else Tone.GOOD)
        if (p.antidote && heroPoison > 0) {
            heroPoison = 0
            bridge?.log("the ${p.name} burns the venom out of the blood", Tone.GOOD)
        }
        if (p.lore.isNotEmpty()) bridge?.log(p.lore, Tone.PLAIN)
    }

    private fun applyRelic(r: Item) {
        addStats(r)
        brain.noteReward(8f + r.equipScore / 25f)
        bridge?.log(Narrator.relicPickup(r), Tone.EPIC)
        if (r.lore.isNotEmpty()) bridge?.log(r.lore, Tone.PLAIN)
        note("Claimed ${r.name}")
    }

    private fun equipOrSalvage(item: Item) {
        val cur = when (item.slot) {
            ItemSlot.WEAPON -> hero.weapon
            ItemSlot.ARMOR -> hero.armor
            else -> hero.trinket
        }
        if (cur == null || item.equipScore > cur.equipScore) {
            brain.noteReward(5f + item.equipScore / 30f)
            if (cur != null) removeStats(cur)
            addStats(item)
            when (item.slot) {
                ItemSlot.WEAPON -> hero.weapon = item
                ItemSlot.ARMOR -> hero.armor = item
                else -> hero.trinket = item
            }
            bridge?.log(Narrator.equip(item),
                if (item.rarity >= Rarity.EPIC) Tone.EPIC else Tone.GOOD)
            if (item.lore.isNotEmpty()) bridge?.log(item.lore, Tone.PLAIN)
            if (item.rarity >= Rarity.RARE) note("Equipped ${item.name}")
            if (item.rarity >= Rarity.EPIC)
                chron("item", "${item.name} — ${item.passiveLine()}")
            if (item.rarity == Rarity.LEGENDARY) featEvents.add("legend" to 1)
        } else {
            val g = maxOf(2, item.price / 2)
            hero.gold += g
            brain.noteReward(g / 40f)
            bridge?.log("salvages ${item.name} for $g gold — the ${cur.name} serves still", Tone.PLAIN)
        }
    }

    private fun addStats(i: Item) {
        hero.atk += i.atk; hero.def += i.def
        if (i.hp > 0) { hero.maxHp += i.hp; hero.hp += i.hp }
        hero.pCrit += i.crit; hero.pThorns += i.thorns; hero.pLifesteal += i.lifesteal
        hero.pVision += i.vision; hero.pPotion += i.potionPower
        hero.pGold += i.gold; hero.pXp += i.xp
    }

    private fun removeStats(i: Item) {
        hero.atk -= i.atk; hero.def -= i.def
        if (i.hp > 0) { hero.maxHp -= i.hp; hero.hp = minOf(hero.hp, hero.maxHp) }
        hero.pCrit -= i.crit; hero.pThorns -= i.thorns; hero.pLifesteal -= i.lifesteal
        hero.pVision -= i.vision; hero.pPotion -= i.potionPower
        hero.pGold -= i.gold; hero.pXp -= i.xp
    }

    // ---------- NPC interactions ----------

    private fun interact(npc: Npc) {
        if (metNpcKinds.add(npc.kind.name))
            chron("npc", "met ${npc.name}, ${npc.title}")
        when (npc.kind) {
            NpcKind.GIFT -> {
                hero.potions++
                hero.hp = minOf(hero.maxHp, hero.hp + 4)
                bridge?.log("${npc.name} ${NpcForge.giftLine()}", Tone.GOOD)
                if (npc.quirk == NpcQuirk.PATRON) {
                    hero.potions++
                    bridge?.log("${npc.name} ${Narrator.patronExtra()}", Tone.EPIC)
                }
                departNpc(npc, NpcForge.departLine(npc.kind))
            }
            NpcKind.HEALER -> {
                hero.hp = hero.maxHp
                bridge?.log("${npc.name} ${NpcForge.healLine()}", Tone.EPIC)
                if (npc.quirk == NpcQuirk.PATRON) {
                    hero.hp = minOf(hero.maxHp, hero.hp + 5)
                    bridge?.log("${npc.name} ${Narrator.patronExtra()}", Tone.EPIC)
                }
                departNpc(npc, NpcForge.departLine(npc.kind))
            }
            NpcKind.LORE -> {
                hero.xp += 3 + depth
                bridge?.log("${npc.name} leans close: ${npc.rumors.randomOrNull() ?: "knows nothing today"}", Tone.PLAIN)
                grantLearned(listOf(minOf(3, depth / 4)), "is taught by ${npc.name} —")
                checkLevelUp()
                departNpc(npc, NpcForge.departLine(npc.kind))
                return
            }
            NpcKind.SMITH -> {
                val cost = if (npc.temper == Temper.MAD) madPrice(smithCost(npc)) else smithCost(npc)
                if (hero.gold >= cost) {
                    hero.gold -= cost
                    if (Random.nextBoolean()) { hero.atk++; bridge?.log("${npc.name} ${NpcForge.smithAtkLine()}", Tone.GOOD) }
                    else { hero.def++; bridge?.log("${npc.name} ${NpcForge.smithDefLine()}", Tone.GOOD) }
                    if (cost == 0) bridge?.log("${npc.name}: 'The stairs paid your tab. Don't ask which ones.'", Tone.EPIC)
                    serveMemory(npc)
                    maybeRumor(npc)
                } else refuseNpc(npc, NpcForge.refuseLine(npc, cost))
            }
            NpcKind.MAPKEEPER -> {
                val cost = if (npc.temper == Temper.MAD) madPrice(mapCost(npc)) else mapCost(npc)
                if (hero.gold >= cost) {
                    hero.gold -= cost
                    for (y in 0 until H) for (x in 0 until W) explored[y][x] = true
                    for (itm in items) knownItems.add(key(itm.x, itm.y))
                    bridge?.log("${npc.name} ${NpcForge.mapLine(depth)}", Tone.GOOD)
                    note("Bought the chart of floor $depth")
                    serveMemory(npc)
                    maybeRumor(npc)
                } else refuseNpc(npc, NpcForge.refuseLine(npc, cost))
            }
            NpcKind.MERCHANT -> {
                val o = npc.offer ?: run { serveMemory(npc); return }
                var cost = if (npc.temper == Temper.MAD) madPrice(o.price) else o.price
                if (npc.quirk == NpcQuirk.HAGGLER) cost = (cost * 0.75f).toInt().coerceAtLeast(3)
                if (hero.gold >= cost) {
                    hero.gold -= cost
                    applyItem(o.item)
                    bridge?.log("${npc.name} sells ${o.item.name} for $cost gold.", Tone.GOOD)
                    serveMemory(npc)
                    maybeRumor(npc)
                } else refuseNpc(npc, NpcForge.refuseLine(npc, cost))
            }
            NpcKind.GAMBLER -> {
                if (hero.gold < 8) { refuseNpc(npc, NpcForge.refuseGambleLine()); return }
                hero.gold -= 8
                when (Random.nextInt(100)) {
                    in 0 until 35 -> {
                        val g = 15 + Random.nextInt(26); hero.gold += g
                        bridge?.log("${npc.name} ${NpcForge.gambleJackpot(g)}", Tone.EPIC)
                        note("Won $g gold at dice")
                    }
                    in 35 until 60 -> {
                        hero.potions++
                        bridge?.log("${npc.name} ${NpcForge.gamblePotionLine()}", Tone.GOOD)
                    }
                    in 60 until 80 -> {
                        hero.maxHp += 4; hero.hp += 4
                        bridge?.log("${npc.name} ${NpcForge.gambleVitalityLine()}", Tone.GOOD)
                    }
                    else -> {
                        hero.gold = maxOf(0, hero.gold - 8)
                        bridge?.log("${npc.name} ${NpcForge.gambleRobLine()}", Tone.BAD)
                    }
                }
                serveMemory(npc)
            }
            NpcKind.GHOST -> {
                val xp = 10 + npc.ghostDepth * 3
                hero.xp += xp
                bridge?.log(NpcForge.ghostLine(npc.name, npc.ghostDepth, xp), Tone.EPIC)
                departNpc(npc, NpcForge.departLine(npc.kind))
            }
        }
        checkLevelUp()
    }

    private fun maybeRumor(npc: Npc) {
        if (Random.nextInt(100) < 50) npc.rumors.randomOrNull()?.let {
            bridge?.log("${npc.name} leans close: $it", Tone.PLAIN)
        }
    }

    private fun refuseNpc(npc: Npc, line: String) {
        npcMemory.add(npc)
        bridge?.log("${npc.name} $line", Tone.PLAIN)
        if (npc.quirk == NpcQuirk.VINDICTIVE) {
            val take = minOf(hero.gold, 6 + Random.nextInt(8))
            if (take > 0) {
                hero.gold -= take
                bridge?.log("${npc.name} palms $take gold on the way out. 'Interest,' they say.", Tone.BAD)
            }
        }
    }

    private fun serveMemory(npc: Npc) {
        if (npc.quirk == NpcQuirk.FORGETFUL) {
            bridge?.log("${npc.name} blinks. '…Have we met?' (will deal again)", Tone.PLAIN)
            return
        }
        npcMemory.add(npc)
    }

    private fun departNpc(npc: Npc, line: String) {
        bridge?.log("${npc.name} $line", Tone.PLAIN)
        npcs.remove(npc)
    }

    // ---------- NPC heuristics (no learning) ----------

    private fun npcSidestep(npc: Npc) {
        if (npc.quirk == NpcQuirk.SWIFT) return
        repeat(4) {
            val d = DIRS.random()
            if (npcMove(npc, npc.x + d[0], npc.y + d[1])) return
        }
    }

    private fun npcsTurn() {
        for (n in npcs) {
            if (n.quirk == NpcQuirk.SWIFT) continue
            val menace = monsters.firstOrNull { it.alive && it.aggro &&
                manhattan(it.x, it.y, n.x, n.y) <= 2 }
            if (menace != null) { npcFlee(n, menace); continue }
            val d = manhattan(n.x, n.y, hero.x, hero.y)
            val keeper = n.kind == NpcKind.SMITH || n.kind == NpcKind.MAPKEEPER ||
                         n.kind == NpcKind.MERCHANT
            val drift: Boolean = when (n.kind) {
                NpcKind.GAMBLER -> d in 3..6
                NpcKind.GHOST   -> d <= 10 && Random.nextInt(100) < 50
                NpcKind.SMITH, NpcKind.MAPKEEPER, NpcKind.MERCHANT -> false
                else -> hero.hp < hero.maxHp * 60 / 100 && d <= 8
            }
            if (drift && npcStepToward(n)) continue
            if (Random.nextInt(100) < (if (keeper) 8 else 25)) npcWander(n)
        }
    }

    private fun npcStepToward(n: Npc): Boolean {
        val dx = hero.x - n.x; val dy = hero.y - n.y
        val first: IntArray; val second: IntArray
        if (abs(dx) >= abs(dy)) { first = intArrayOf(sgn(dx), 0); second = intArrayOf(0, sgn(dy)) }
        else { first = intArrayOf(0, sgn(dy)); second = intArrayOf(sgn(dx), 0) }
        for (t in arrayOf(first, second)) {
            if (t[0] == 0 && t[1] == 0) continue
            if (npcMove(n, n.x + t[0], n.y + t[1])) return true
        }
        return false
    }

    private fun npcWander(n: Npc) {
        val dir = DIRS.random()
        npcMove(n, n.x + dir[0], n.y + dir[1])
    }

    private fun npcMove(n: Npc, nx: Int, ny: Int): Boolean {
        if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) return false
        if (map[ny][nx] != Tile.FLOOR) return false
        if (nx == hero.x && ny == hero.y) return false
        if (monsterAt(nx, ny) != null || npcAt(nx, ny) != null) return false
        if (items.any { it.x == nx && it.y == ny }) return false
        n.x = nx; n.y = ny
        return true
    }

    private fun npcFlee(n: Npc, threat: Monster) {
        var best: Pt? = null
        var bestD = manhattan(n.x, n.y, threat.x, threat.y)
        for (d in DIRS) {
            val nx = n.x + d[0]; val ny = n.y + d[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] != Tile.FLOOR) continue
            if (nx == hero.x && ny == hero.y) continue
            if (monsterAt(nx, ny) != null || npcAt(nx, ny) != null) continue
            val dd = manhattan(nx, ny, threat.x, threat.y)
            if (dd > bestD) { bestD = dd; best = Pt(nx, ny) }
        }
        best?.let { n.x = it.x; n.y = it.y }
    }

    // ---------- rivals (heuristic FSM, no learning) ----------

    fun rivalAt(x: Int, y: Int): Rival? =
        rivals.firstOrNull { it.alive && it.x == x && it.y == y }

    fun rivalTick() {
        for (r in rivals.toList()) {
            if (!r.alive) continue
            r.turnsHere++
            val dH = manhattan(r.x, r.y, hero.x, hero.y)
            if (!r.met && dH <= 7 && heroAlive && lineOfSight(r.x, r.y, hero.x, hero.y)) {
                r.met = true
                bridge?.log(Narrator.rivalIntro(r.name, r.persona.tag, r.persona.hint), Tone.BAD)
                chron("rival", "${r.name} the ${r.persona.tag} shadows the same halls")
            }
            val seesHero = heroAlive && dH <= 6 && lineOfSight(r.x, r.y, hero.x, hero.y)

            if (seesHero && r.hostile) {
                if (dH == 1) { rivalAttackHero(r); continue }
                rivalStepToward(r, hero.x, hero.y); continue
            }
            if (r.hp < r.maxHp * 35 / 100) { rivalFleeStep(r); continue }
            if (r.persona == RivalPersona.GLORY) {
                val m = monsters.firstOrNull {
                    it.alive && manhattan(it.x, it.y, r.x, r.y) <= 5 &&
                    lineOfSight(r.x, r.y, it.x, it.y)
                }
                if (m != null) {
                    if (manhattan(m.x, m.y, r.x, r.y) <= 1) { rivalKillMonster(r, m); continue }
                    rivalStepToward(r, m.x, m.y); continue
                }
            }
            val range = if (r.persona == RivalPersona.GREEDY) 6 else 1
            val loot = items.firstOrNull { manhattan(it.x, it.y, r.x, r.y) <= range }
            if (loot != null) {
                if (loot.x == r.x && loot.y == r.y) { rivalTake(r, loot); continue }
                rivalStepToward(r, loot.x, loot.y); continue
            }
            if (map[r.y][r.x] == Tile.STAIRS &&
                (exploredFraction() >= 0.4f || r.turnsHere > 40 || r.persona == RivalPersona.CAUTIOUS)) {
                rivalDescend(r); continue
            }
            if (r.turnsHere > 90) { rivalDescend(r); continue }
            if (Random.nextInt(100) < 70) rivalStepToward(r, stairs.x, stairs.y)
            else rivalWander(r)
        }
    }

    private fun rivalStepToward(r: Rival, tx: Int, ty: Int) {
        val dx = tx - r.x; val dy = ty - r.y
        val first: IntArray; val second: IntArray
        if (abs(dx) >= abs(dy)) { first = intArrayOf(sgn(dx), 0); second = intArrayOf(0, sgn(dy)) }
        else { first = intArrayOf(0, sgn(dy)); second = intArrayOf(sgn(dx), 0) }
        for (t in arrayOf(first, second)) {
            if (t[0] == 0 && t[1] == 0) continue
            if (rivalMove(r, r.x + t[0], r.y + t[1])) return
        }
    }

    private fun rivalWander(r: Rival) {
        val d = DIRS.random()
        rivalMove(r, r.x + d[0], r.y + d[1])
    }

    private fun rivalFleeStep(r: Rival) {
        var best: Pt? = null; var bestD = manhattan(r.x, r.y, hero.x, hero.y)
        for (d in DIRS) {
            val nx = r.x + d[0]; val ny = r.y + d[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] != Tile.FLOOR || monsterAt(nx, ny) != null) continue
            if (nx == hero.x && ny == hero.y) continue
            val dd = manhattan(nx, ny, hero.x, hero.y)
            if (dd > bestD) { bestD = dd; best = Pt(nx, ny) }
        }
        best?.let { r.x = it.x; r.y = it.y }
    }

    private fun rivalMove(r: Rival, nx: Int, ny: Int): Boolean {
        if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) return false
        if (map[ny][nx] != Tile.FLOOR) return false
        if (nx == hero.x && ny == hero.y) return false
        if (monsterAt(nx, ny) != null || npcAt(nx, ny) != null || rivalAt(nx, ny) != null) return false
        r.x = nx; r.y = ny
        return true
    }

    private fun rivalKillMonster(r: Rival, m: Monster) {
        m.hp -= r.atk + Random.nextInt(-1, 2)
        if (m.hp <= 0) {
            monsters.remove(m)
            if (Random.nextInt(100) < 25)
                bridge?.log(Narrator.rivalCut(r.name, m.type.name), Tone.PLAIN)
        }
    }

    private fun rivalTake(r: Rival, fl: FloorItem) {
        items.remove(fl)
        knownItems.remove(key(fl.x, fl.y))
        when (fl.item.slot) {
            ItemSlot.GOLD -> r.hoard += fl.amount
            ItemSlot.POTION -> r.hp = minOf(r.maxHp, r.hp + maxOf(8, fl.item.heal))
            else -> {
                r.carried = fl.item; r.hoard += fl.item.price / 2
                if (Random.nextInt(100) < 30)
                    bridge?.log(Narrator.rivalPocket(r.name, fl.item.name), Tone.BAD)
            }
        }
    }

    private fun rivalDescend(r: Rival) {
        rivals.remove(r)
        r.personalDepth = depth + 1
        if (Random.nextInt(100) < 70)
            bridge?.log(Narrator.rivalDescend(r.name, r.personalDepth, r.personalDepth > depth),
                if (r.personalDepth > depth) Tone.BAD else Tone.PLAIN)
        bridge?.onRivalEvent(r.name, r.glyph, r.personalDepth, false)
    }

    private fun rivalAttackHero(r: Rival) {
        val dmg = maxOf(1, r.atk + Random.nextInt(-1, 2) - hero.effDef)
        hero.hp -= dmg
        brain.noteReward(-dmg * 1.2f)
        killStreak = 0
        if (Random.nextInt(100) < 50) bridge?.log(Narrator.rivalHitsYou(r.name, dmg), Tone.BAD)
        if (hero.hp <= 0) {
            bridge?.log("${r.name} takes the deep's prize.", Tone.BAD)
            die("the rival ${r.name}")
        }
    }

    private fun attackRival(r: Rival) {
        r.hostile = true
        val crit = Random.nextInt(100) < 10 + hero.pCrit
        var dmg = maxOf(1, hero.effAtk + Random.nextInt(-1, 2) - r.def)
        if (crit) dmg *= 2
        r.hp -= dmg
        brain.noteReward(if (r.hp <= 0) 8f else 1f)
        if (Random.nextInt(100) < 60) bridge?.log(Narrator.rivalHit(r.name, dmg), Tone.BAD)
        if (r.hp <= 0) killRival(r)
    }

    private fun killRival(r: Rival) {
        rivals.remove(r)
        hero.gold += r.hoard
        brain.noteReward(r.hoard / 25f)
        bridge?.log(Narrator.rivalDown(r.name, r.hoard), Tone.EPIC)
        note("Felled the rival $r.name")
        rivalsFelled++
        featEvents.add("rival" to 1)
        chron("rivalKill", "felled the rival $r.name in single combat")
        if (map[r.y][r.x] != Tile.WALL)
            items.add(FloorItem(r.x, r.y, r.carried ?: Items.randomOf(depth)))
        bridge?.onRivalEvent(r.name, r.glyph, r.personalDepth, true)
    }

    fun rivalStipend() {
        if (rivals.isEmpty()) return
        rivals.forEach { it.hoard += 6 + Random.nextInt(9) }
    }

    // ---------- feats, camps, familiars ----------

    fun featAt(x: Int, y: Int): FloorFeat? =
        feats.firstOrNull { !it.used && it.x == x && it.y == y }

    private fun triggerFeat(f: FloorFeat) {
        when (f.kind) {
            FeatKind.SPIKES -> {
                f.used = true
                val dmg = maxOf(2, 4 + depth / 2 - hero.effDef)
                hero.hp -= dmg
                brain.noteReward(-dmg * 1.5f)
                bridge?.log(Narrator.trapSpikes(dmg), Tone.BAD)
                if (hero.hp <= 0) die("a spike trap")
            }
            FeatKind.GAS -> {
                f.used = true
                heroPoison = 3
                bridge?.log(Narrator.trapGas(), Tone.BAD)
            }
            FeatKind.GLYPH -> {
                f.used = true
                val cand = ArrayList<Pt>()
                for (y in 1 until H - 1) for (x in 1 until W - 1)
                    if (map[y][x] == Tile.FLOOR) cand.add(Pt(x, y))
                cand.randomOrNull()?.let { hero.x = it.x; hero.y = it.y }
                bridge?.log(Narrator.trapGlyph(), Tone.BAD)
            }
            FeatKind.CHEST -> {
                f.used = true
                if (Random.nextInt(100) < 18) {
                    bridge?.log(Narrator.chestMimic(), Tone.BAD)
                    val spot = DIRS.map { Pt(hero.x + it[0], hero.y + it[1]) }
                        .firstOrNull { it.x in 1 until W - 1 && it.y in 1 until H - 1 &&
                            map[it.y][it.x] == Tile.FLOOR && monsterAt(it.x, it.y) == null }
                    if (spot != null) {
                        val mm = Monster(pickMonsterType(), spot.x, spot.y, depth, biome)
                        mm.aggro = true
                        monsters.add(mm)
                    }
                } else {
                    val drop = Items.randomOf(depth, lootLuck)
                    applyItem(drop)
                    chron("chest", "opened a chest — ${drop.name}")
                }
            }
            FeatKind.LOCKED -> {
                if (hero.hp > hero.maxHp * 35 / 100) {
                    f.used = true
                    hero.hp = maxOf(1, hero.hp - (4 + depth / 3))
                    brain.noteReward(-3f)
                    val drop = Items.randomOf(depth, lootLuck + 1)
                    applyItem(drop)
                    bridge?.log(Narrator.chestForced(drop), Tone.GOOD)
                    chron("chest", "forced a chest — ${drop.name}")
                } else {
                    ignoredFeats.add(f)
                    bridge?.log(Narrator.chestRefused(), Tone.PLAIN)
                }
            }
            FeatKind.SHRINE -> {
                f.used = true
                if (Random.nextInt(100) < 75) {
                    when (Random.nextInt(100)) {
                        in 0 until 25  -> { hero.hp = hero.maxHp
                            bridge?.log(Narrator.shrineBoon("the stillness restores her wholly"), Tone.EPIC) }
                        in 25 until 45 -> { hero.atk += 1
                            bridge?.log(Narrator.shrineBoon("an edge not of this world — +1 atk"), Tone.EPIC) }
                        in 45 until 65 -> { hero.def += 1
                            bridge?.log(Narrator.shrineBoon("the air hardens around her — +1 def"), Tone.EPIC) }
                        in 65 until 85 -> { val g = 15 + depth * 3; hero.gold += g
                            bridge?.log(Narrator.shrineBoon("old offerings surface — +$g gold"), Tone.EPIC) }
                        else -> grantLearned(listOf(0, 1, 2), "the shrine teaches —")
                    }
                    chron("shrine", "prayed at a shrine and was answered")
                } else {
                    when (Random.nextInt(3)) {
                        0 -> { heroPoison = 3
                            bridge?.log(Narrator.shrineCurse("venom climbs her arm"), Tone.BAD) }
                        1 -> { val lost = hero.gold / 5; hero.gold -= lost
                            bridge?.log(Narrator.shrineCurse("the shrine takes $lost gold as toll"), Tone.BAD) }
                        else -> {
                            repeat(2) {
                                val spot = DIRS.map { Pt(hero.x + it[0] * 2, hero.y + it[1] * 2) }
                                    .firstOrNull { it.x in 1 until W - 1 && it.y in 1 until H - 1 &&
                                        map[it.y][it.x] == Tile.FLOOR && monsterAt(it.x, it.y) == null &&
                                        !(it.x == hero.x && it.y == hero.y) }
                                if (spot != null) {
                                    val mm = Monster(pickMonsterType(), spot.x, spot.y, depth, biome)
                                    mm.aggro = true; monsters.add(mm)
                                }
                            }
                            bridge?.log(Narrator.shrineCurse("something old wakes, and it is not alone"), Tone.BAD)
                        }
                    }
                    chron("shrine", "prayed at a shrine and was cursed")
                }
            }
        }
    }

    private data class CampCard(val name: String, val line: String, val score: Int)

    private fun campfire() {
        val missing = hero.maxHp - hero.hp
        val g = 18 + depth * 3
        val pool = listOf(
            CampCard("Rest", "the fire mends — restore ${maxOf(10, missing)} hp", missing * 2),
            CampCard("Hone", "an edge worked slow — +1 atk", 22 + hero.level * 2),
            CampCard("Temper", "plates reset — +1 def", 20 + hero.level * 2),
            CampCard("Vigor", "deep breathing — +6 max hp",
                18 + if (hero.maxHp < 60) 10 else 0),
            CampCard("Study", "a page by firelight — learn an art",
                if (hero.skills.size < 5) 30 else 12),
            CampCard("Forage", "the ashes hold old offerings — +$g gold",
                16 + if (hero.gold < 30) 12 else 0),
            CampCard("Cleanse", "the venom is drawn out — cure + 1 potion",
                if (heroPoison > 0) 45 else if (hero.potions == 0) 25 else 5),
            CampCard("Embers", "feed the lineage — +2 embers for those to come", 14 + depth),
            CampCard("Befriend", if (familiar == null) "a stray thing orbits her lantern" else "a shadow already follows",
                if (familiar == null) 24 else 0))
        val picks = pool.shuffled().take(3)
        bridge?.log(Narrator.campfireFound(), Tone.GOOD)
        for (c in picks) bridge?.log("· ${c.name} — ${c.line}", Tone.PLAIN)
        val best = picks.maxByOrNull { it.score + Random.nextInt(0, 6) }!!
        when (best.name) {
            "Rest"    -> hero.hp = minOf(hero.maxHp, hero.hp + maxOf(10, missing))
            "Hone"    -> hero.atk += 1
            "Temper"  -> hero.def += 1
            "Vigor"   -> { hero.maxHp += 6; hero.hp += 6 }
            "Study"   -> grantLearned(listOf(0, 1, 2, 3), "studies by the fire —")
            "Forage"  -> hero.gold += g
            "Cleanse" -> { heroPoison = 0; hero.potions += 1 }
            "Embers"  -> bridge?.onEmber(2)
            "Befriend" -> if (familiar == null) befriend()
        }
        bridge?.log(Narrator.campfirePick(best.name), Tone.EPIC)
        chron("camp", "rested at a campfire — took ${best.name}")
        checkLevelUp()
    }

    private fun befriend() {
        val kinds = listOf(
            Triple("torch-moth", '°', 0xFFF0A35C.toInt()),
            Triple("crypt-cat", 'c', 0xFF8FA876.toInt()),
            Triple("ember-sprite", 'e', 0xFFE2703A.toInt()))
        val k = kinds.random()
        familiar = Familiar(k.first, k.second, k.third, hero.x, hero.y)
        bridge?.log(Narrator.familiarJoin(familiar!!.name, k.first), Tone.EPIC)
        chron("familiar", "befriended ${familiar!!.name} the ${k.first}")
    }

    private fun familiarTurn() {
        val f = familiar ?: return
        if (!heroAlive) return
        if (f.cd > 0) f.cd--
        if (monsters.any { it.alive && it.type.boss && visible[it.y][it.x] }) {
            if (!familiarHid) { familiarHid = true
                bridge?.log(Narrator.familiarHides(f.name), Tone.PLAIN) }
            return
        }
        familiarHid = false
        if (f.cd <= 0 && Random.nextInt(100) < 35) {
            val m = monsters.firstOrNull { manhattan(it.x, it.y, f.x, f.y) == 1 }
            if (m != null) {
                f.cd = 2
                m.hp -= 1 + depth / 4 + Random.nextInt(2)
                if (m.hp <= 0) killMonster(m, "the ${f.name}'s nip")
                else if (Random.nextInt(100) < 30) bridge?.log(Narrator.familiarBite(f.name), Tone.PLAIN)
            }
        }
        if (Random.nextInt(100) < 12) {
            hero.gold += 2 + Random.nextInt(5)
            if (Random.nextInt(100) < 30) bridge?.log(Narrator.familiarGold(f.name), Tone.PLAIN)
        }
        if (manhattan(f.x, f.y, hero.x, hero.y) > 6) {
            for (d in DIRS) {
                val nx = hero.x + d[0]; val ny = hero.y + d[1]
                if (nx in 1 until W - 1 && ny in 1 until H - 1 &&
                    map[ny][nx] != Tile.WALL && monsterAt(nx, ny) == null) {
                    f.x = nx; f.y = ny; break
                }
            }
            return
        }
        if (Random.nextInt(100) < 70) {
            val dx = hero.x - f.x; val dy = hero.y - f.y
            val first: IntArray; val second: IntArray
            if (abs(dx) >= abs(dy)) { first = intArrayOf(sgn(dx), 0); second = intArrayOf(0, sgn(dy)) }
            else { first = intArrayOf(0, sgn(dy)); second = intArrayOf(sgn(dx), 0) }
            for (t in arrayOf(first, second)) {
                if (t[0] == 0 && t[1] == 0) continue
                val nx = f.x + t[0]; val ny = f.y + t[1]
                if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
                if (map[ny][nx] == Tile.WALL || monsterAt(nx, ny) != null || npcAt(nx, ny) != null) continue
                if (nx == hero.x && ny == hero.y) continue
                if (nx == stairs.x && ny == stairs.y) continue
                f.x = nx; f.y = ny
                break
            }
        }
    }

    // ---------- talents & boons ----------

    fun applyTalentDelta(id: String, n: Int) {
        appliedTalents[id] = (appliedTalents[id] ?: 0) + n
        when (id) {
            "torch" -> hero.potions += n
            "owl" -> hero.pVision += n
            "purse" -> hero.gold += n * 10
            "scholar" -> hero.pXp += n
            "bulwark" -> { hero.maxHp += n * 2; hero.hp += n * 2 }
            "fortune" -> lootLuck += n
        }
    }

    fun applyTalents(map: Map<String, Int>) {
        map.forEach { (k, v) ->
            val had = appliedTalents[k] ?: 0
            if (v > had) applyTalentDelta(k, v - had)
        }
    }

    fun restoreAppliedTalents(map: Map<String, Int>) {
        appliedTalents.clear(); appliedTalents.putAll(map)
    }

    fun applyBoon(kind: String) {
        when (kind) {
            "RELIC" -> applyItem(Items.wardenDrop(depth, lootLuck))
            "POTION" -> hero.potions += 2
            "HEAL_HP" -> { hero.maxHp += 6; hero.hp = hero.maxHp }
            "XP" -> hero.xp += 15 + depth * 5
            "ATK" -> hero.atk += 1
        }
        bridge?.log("🕯️ a vow kept — the deep grants ${Legacy.boonName(kind)}", Tone.EPIC)
        checkLevelUp()
    }

    // ---------- combat ----------

    private fun heroAttack(m: Monster) {
        m.aggro = true
        val crit = Random.nextInt(100) < 10 + hero.pCrit
        var dmg = maxOf(1, hero.effAtk + Random.nextInt(-1, 2) - m.effectiveDef())
        if (crit) dmg *= 2
        m.hp -= dmg
        runStats["dealt"] = (runStats["dealt"] ?: 0) + dmg
        if (dmg > (runStats["bigHit"] ?: 0)) runStats["bigHit"] = dmg
        if (m.hp <= 0) killMonster(m, if (crit) "crit" else "blade")
        else if (crit) bridge?.log("A perfect strike — the ${m.type.name} takes $dmg and keeps coming.", Tone.GOOD)
    }

    private fun killMonster(m: Monster, source: String) {
        if (m.quirk == Quirk.UNDYING && !m.undyingUsed) {
            m.undyingUsed = true
            m.hp = 1
            bridge?.log(Narrator.undyingLine(m.type.name), Tone.BAD)
            return
        }
        monsters.remove(m)
        hero.kills++
        featEvents.add("kill" to 1)
        bridge?.onMonsterKilled(m.type.id)
        lore.noteVictory(m.type.id)
        fightLog.remove(m.type.id)
        if (map[m.y][m.x] == Tile.FLOOR) decor[m.y][m.x] = '%'
        val g = m.goldDrop() * (if (floorMod == FloorMod.GILDED) 3 else 2) / 2 + hero.pGold
        val xp = m.xpDrop() + hero.pXp
        hero.gold += g
        hero.xp += (xp * (1f + auraXp)).toInt()
        if (hero.pLifesteal > 0) hero.hp = minOf(hero.maxHp, hero.hp + hero.pLifesteal)
        brain.noteReward(xp / 10f + g / 25f + (if (m.elite) 2f else 0f) + (if (m.type.boss) 5f else 0f))
        if (m.bounty > 0) {
            hero.gold += m.bounty
            brain.noteReward(m.bounty / 25f)
            bridge?.onEmber(2)
            bridge?.log(Narrator.bountyClaim(m.eliteName, m.bounty), Tone.EPIC)
            chron("bounty", "collected the bounty on ${m.eliteName}")
        }
        val subj = when {
            m.elite -> m.eliteName
            m.epithet.isNotEmpty() -> m.epithet
            else -> "the ${m.type.name}"
        }
        if (m.elite) {
            bridge?.log("${Narrator.eliteKill(m.eliteName)} (+$xp xp · +$g gold)", Tone.EPIC)
            note("Felled ${m.eliteName}")
            if (Random.nextInt(100) < 30)
                grantLearned(listOf(1, 2), "loots a scroll from ${m.eliteName}'s body —")
        } else if (source == "blade" || source == "crit") {
            bridge?.log("${Narrator.kill(m.type, subj, source == "crit")} (+$xp xp · +$g gold)",
                if (m.type.boss) Tone.EPIC else Tone.GOOD)
        } else {
            bridge?.log("the ${m.type.name} falls to $source (+$xp xp)", Tone.GOOD)
        }
        if (m.type.boss) {
            note("Broke the ${m.type.name} on floor $depth")
            chron("warden", "broke the ${m.type.name} on floor $depth")
            featEvents.add("warden" to 1)
            grantLearned(listOf(minOf(3, depth / 5 + 1)), "strips the ${m.type.name}'s secret —")
            bridge?.onEmber(3)
            val drop = Items.wardenDrop(depth, lootLuck)
            if (map[m.y][m.x] != Tile.WALL) {
                items.add(FloorItem(m.x, m.y, drop))
                bridge?.log("the warden's hoard clatters to the floor — ${drop.name}", Tone.EPIC)
            }
        }
        if (Random.nextInt(100) < 14) {
            hero.potions++
            bridge?.log(Narrator.potionLoot(), Tone.GOOD)
        }
        killStreak++
        if (killStreak > bestStreak) bestStreak = killStreak
        if (!firstBlood) { firstBlood = true; chron("blood", "first blood — the ${m.type.name}") }
        else if (hero.kills % 25 == 0) chron("kill", "${hero.kills} souls behind the torch")
        if (killStreak == 3) bridge?.log(Narrator.streak(3), Tone.EPIC)
        else if (killStreak > 0 && killStreak % 5 == 0) bridge?.log(Narrator.streak(killStreak), Tone.EPIC)
        checkLevelUp()
    }

    private fun checkLevelUp() {
        while (hero.xp >= hero.xpNext) {
            hero.xp -= hero.xpNext
            hero.level++
            hero.xpNext = 18 + hero.level * 16
            hero.maxHp += 5
            hero.atk += 1
            if (hero.level % 2 == 0) hero.def += 1
            hero.hp = minOf(hero.maxHp, hero.hp + hero.maxHp / 2)
            bridge?.log(Narrator.levelUp(hero.level), Tone.EPIC)
            bridge?.onLevelUp(hero.level)
            chron("level", "reached level ${hero.level}")
            if (Random.nextInt(100) < 25)
                bridge?.log(Narrator.personaWhim(personality.title), Tone.PLAIN)
            if (Random.nextInt(100) < 75) {
                val tiers = when {
                    hero.level < 4 -> listOf(0, 1)
                    hero.level < 7 -> listOf(1)
                    hero.level < 10 -> listOf(1, 2)
                    else -> listOf(2, 3)
                }
                grantLearned(tiers, "grows into a new art —")
            }
        }
    }

    private fun monstersTurn() {
        if (!heroAlive) return
        for (m in monsters.toList()) {
            if (!m.alive) continue
            if (m.stun > 0) { m.stun--; continue }
            if (m.type.boss && turnsThisFloor > 60) {
                if (!wardenHunting) {
                    wardenHunting = true
                    bridge?.log(Narrator.wardenHunt(), Tone.BAD)
                }
                if (manhattan(m.x, m.y, hero.x, hero.y) == 1) {
                    monsterAttack(m); if (!heroAlive) break; continue
                }
                if (Random.nextInt(100) < 60) { bossProwl(m); continue }
            }
            if (m.bribed) {
                if (Random.nextInt(100) < 30) wanderStep(m)
                continue
            }
            if (m.quirk == Quirk.REGEN && m.hp < m.maxHp) m.hp = minOf(m.maxHp, m.hp + 1)
            if (m.quirk == Quirk.BERSERK && !m.enraged && m.hp < m.maxHp / 2) {
                m.enraged = true
                bridge?.log(Narrator.enrage(m.type.name), Tone.BAD)
            }
            val d = manhattan(m.x, m.y, hero.x, hero.y)
            if (m.quirk == Quirk.COWARD && m.hp < m.maxHp * 35 / 100) {
                if (!m.fledTaunt) {
                    m.fledTaunt = true
                    bridge?.log(Narrator.fleeLine(m.type.name), Tone.PLAIN)
                }
                if (d == 1) { monsterAttack(m); if (!heroAlive) break }
                fleeFrom(m)
                continue
            }
            val sight = (if (m.quirk == Quirk.SENTINEL || m.affix == Affix.SWIFT) 9 else 6) +
                (if (floorMod == FloorMod.HUSH) 2 else 0)
            if (d <= sight && !m.aggro && !m.bribed && lineOfSight(m.x, m.y, hero.x, hero.y)) {
                m.aggro = true
                if (tauntCd <= 0 && Random.nextInt(100) < 30) {
                    bridge?.log(Narrator.taunt(m.type.name), Tone.PLAIN)
                    tauntCd = 25 + Random.nextInt(25)
                }
                if (m.quirk == Quirk.PACK) {
                    for (o in monsters)
                        if (o !== m && o.alive && !o.aggro &&
                            manhattan(o.x, o.y, m.x, m.y) <= 4) o.aggro = true
                    bridge?.log(Narrator.packHowl(m.type.name), Tone.PLAIN)
                }
            }
            if (d == 1) { monsterAttack(m); if (!heroAlive) break; continue }
            if (m.aggro) {
                chaseStep(m)
                if (heroAlive && m.affix == Affix.SWIFT && Random.nextInt(100) < 40 &&
                    manhattan(m.x, m.y, hero.x, hero.y) > 1) chaseStep(m)
                if (!heroAlive) break
            } else if (Random.nextInt(100) < 30) {
                wanderStep(m)
            }
        }
    }

    private fun chaseStep(m: Monster) {
        val dx = hero.x - m.x
        val dy = hero.y - m.y
        val first: IntArray; val second: IntArray
        if (abs(dx) >= abs(dy)) { first = intArrayOf(sgn(dx), 0); second = intArrayOf(0, sgn(dy)) }
        else { first = intArrayOf(0, sgn(dy)); second = intArrayOf(sgn(dx), 0) }
        for (t in arrayOf(first, second)) {
            if (t[0] == 0 && t[1] == 0) continue
            val nx = m.x + t[0]; val ny = m.y + t[1]
            if (nx == hero.x && ny == hero.y) { monsterAttack(m); return }
            if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue
            if (map[ny][nx] == Tile.WALL) continue
            if (monsterAt(nx, ny) != null || npcAt(nx, ny) != null) continue
            m.x = nx; m.y = ny
            return
        }
    }

    private fun fleeFrom(m: Monster): Pt? {
        var best: Pt? = null
        var bestD = manhattan(m.x, m.y, hero.x, hero.y)
        for (d in DIRS) {
            val nx = m.x + d[0]; val ny = m.y + d[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] == Tile.WALL || monsterAt(nx, ny) != null || npcAt(nx, ny) != null) continue
            if (nx == hero.x && ny == hero.y) continue
            val dd = manhattan(nx, ny, hero.x, hero.y)
            if (dd > bestD) { bestD = dd; best = Pt(nx, ny) }
        }
        if (best != null) { m.x = best.x; m.y = best.y }
        return best
    }

    private fun wanderStep(m: Monster) {
        val dir = DIRS.random()
        val nx = m.x + dir[0]; val ny = m.y + dir[1]
        if (nx in 1 until W - 1 && ny in 1 until H - 1 &&
            map[ny][nx] != Tile.WALL && monsterAt(nx, ny) == null &&
            npcAt(nx, ny) == null && !(nx == hero.x && ny == hero.y)) { m.x = nx; m.y = ny }
    }

    private fun bossProwl(m: Monster) {
        val dx = stairs.x - m.x; val dy = stairs.y - m.y
        val first: IntArray; val second: IntArray
        if (abs(dx) >= abs(dy)) { first = intArrayOf(sgn(dx), 0); second = intArrayOf(0, sgn(dy)) }
        else { first = intArrayOf(0, sgn(dy)); second = intArrayOf(sgn(dx), 0) }
        for (t in arrayOf(first, second)) {
            if (t[0] == 0 && t[1] == 0) continue
            val nx = m.x + t[0]; val ny = m.y + t[1]
            if (nx <= 0 || ny <= 0 || nx >= W - 1 || ny >= H - 1) continue
            if (map[ny][nx] == Tile.WALL || monsterAt(nx, ny) != null || npcAt(nx, ny) != null) continue
            if (nx == hero.x && ny == hero.y) continue
            m.x = nx; m.y = ny
            return
        }
    }

    fun darkAdvancesTick() {
        if (!heroAlive) return
        for (m in monsters.filter { it.alive && it.aggro && !it.bribed }) {
            if (!heroAlive) return
            if (m.stun > 0) { m.stun--; continue }
            darkKill = true
            if (manhattan(m.x, m.y, hero.x, hero.y) == 1) monsterAttack(m)
            else chaseStep(m)
            darkKill = false
        }
    }

    private fun monsterAttack(m: Monster) {
        var dmg = maxOf(1, m.effectiveAtk() + Random.nextInt(-1, 2) - hero.effDef)
        if (m.quirk == Quirk.AMBUSHER && !m.ambushed) {
            m.ambushed = true
            dmg *= 2
            bridge?.log(Narrator.ambush(m.type.name), Tone.BAD)
        }
        if (m.affix == Affix.ANCIENT && Random.nextInt(100) < 15) dmg = maxOf(1, dmg * 3 / 2)
        hero.hp -= dmg
        brain.noteReward(-dmg * 1.5f)
        runStats["taken"] = (runStats["taken"] ?: 0) + dmg
        if (dmg > (runStats["bigTaken"] ?: 0)) runStats["bigTaken"] = dmg
        killStreak = 0
        fightLog[m.type.id] = (fightLog[m.type.id] ?: 0) + dmg
        val expected = maxOf(1, m.effectiveAtk() - hero.effDef)
        val before = lore.threat(m.type.id)
        lore.noteFight(m.type.id, dmg, expected)
        val after = lore.threat(m.type.id)
        if (before < 1.6f && after >= 1.6f) bridge?.onLesson("🧠 ${Narrator.loreMarked(m.type.name)}")
        else if (before > 0.7f && after <= 0.7f) bridge?.onLesson("🧠 ${Narrator.loreDisarmed(m.type.name)}")
        when (m.quirk) {
            Quirk.VENOMOUS -> if (heroPoison < 3) {
                heroPoison = 3
                bridge?.log(Narrator.venomHit(), Tone.BAD)
            }
            Quirk.THIEF -> {
                val take = minOf(hero.gold, 4 + Random.nextInt(9))
                if (take > 0) {
                    hero.gold -= take
                    m.hoard += take
                    bridge?.log(Narrator.stealLine(m.type.name, take), Tone.BAD)
                }
            }
            else -> {}
        }
        if (heroAlive && hero.hp <= hero.maxHp * 15 / 100 && !nearDeathSaid) {
            nearDeathSaid = true
            chron("nearDeath", "death reached for her on floor $depth — the flask answered")
        }
        if (hero.pThorns > 0) {
            m.hp -= hero.pThorns
            if (m.hp <= 0) {
                bridge?.log(Narrator.thornsKill(m.type.name), Tone.GOOD)
                killMonster(m, "returning spite")
                return
            }
        }
        if (hero.hp <= 0) {
            bridge?.log("The ${m.type.name} lands the last blow.", Tone.BAD)
            die(m.type.name, m.type.boss)
        } else if (Random.nextInt(100) < 30 || dmg >= 8) {
            bridge?.log(Narrator.heroHit(m.type.name, dmg), Tone.PLAIN)
        }
    }

    private fun die(by: String, byBoss: Boolean = false) {
        hero.hp = 0
        heroAlive = false
        brain.noteReward(-40f)
        fightLog.clear()
        featEvents.add("dead" to depth)
        val adjCount = monsters.count { it.alive && manhattan(it.x, it.y, hero.x, hero.y) <= 1 }
        val moved = brain.deathLesson(byBoss, hero.potions, adjCount, heroPoison > 0)
        for (p in moved) bridge?.onLesson("🧠 ${Narrator.deathLesson(p)}")
        val summary = RunSummary(depth, hero.level, hero.kills, hero.gold, turns,
                                 by, journal.toList(),
                                 if (darkKill) "the torch-keeper looked away"
                                 else Narrator.epitaph())
        Narrator.elegy(heroName, summary).forEach { bridge?.log(it, Tone.BAD) }
        bridge?.onFallen(summary)
    }

    private fun descend() {
        depth++
        val next = Biomes.forDepth(depth)
        biome = next
        hero.hp = minOf(hero.maxHp, hero.hp + hero.maxHp / 4)
        bridge?.onDepth(depth)
        brain.noteReward(25f)
        if (next.id != lastBiomeId) {
            lastBiomeId = next.id
            chapterCount++
            val (head, sub) = Narrator.chapter(chapterCount, next)
            bridge?.log(head, Tone.EPIC)
            bridge?.log(sub, Tone.PLAIN)
            note("Reached ${next.name} — floor $depth")
        } else {
            bridge?.log(Narrator.descent(depth, next), Tone.PLAIN)
        }
        if (depth % 3 == 0) bridge?.log(Narrator.wardenWarn(), Tone.BAD)
        if (depth % 5 == 0) note("Reached floor $depth")
        chron("floor", "descended to floor $depth")
        featEvents.add("depth" to depth)
        genFloor()
        while (boonQueue.isNotEmpty()) applyBoon(boonQueue.removeAt(0))
        if (depth >= 3 && Random.nextInt(100) < 35) campfire()
        bridge?.onTurnEnd(hero, depth, turns)
    }

    // ---------- persistence ----------

    fun buildSaga(s: RunSummary): List<String> =
        chronicle.saga(heroName, s, bestStreak, rivalsFelled)

    fun serializeRun(): String {
        val h = hero
        val skills = h.skills.joinToString("\u0002") { s ->
            listOf(s.id, s.name, s.school.ordinal, s.tier, s.kind.ordinal, s.fx.ordinal,
                   s.cd, s.power, s.dur, s.pAtk, s.pDef, s.pHp, s.pCrit, s.pThorns,
                   s.pLifesteal, s.pVision, s.pPotion, s.pGold, s.pXp).joinToString("\u0003")
        }
        fun item(i: Item?): String = i?.let {
            listOf(it.name, it.slot.ordinal, it.glyph.toString(), it.rarity.ordinal,
                   it.atk, it.def, it.hp, it.heal, it.potions, it.potionPower,
                   if (it.antidote) 1 else 0, it.crit, it.thorns, it.lifesteal,
                   it.vision, it.gold, it.xp, it.price, it.lore).joinToString("\u0003")
        } ?: ""
        val cds = h.cooldowns.entries.joinToString("\u0002") { "${it.key}\u0003${it.value}" }
        val tals = appliedTalents.entries.joinToString("\u0002") { "${it.key}\u0003${it.value}" }
        return listOf(
            heroName, personality.temperament.name, personality.focus.name,
            depth, turns, h.level, h.xp, h.xpNext, h.maxHp, h.hp, h.atk, h.def,
            h.potions, h.gold, h.kills, h.pCrit, h.pThorns, h.pLifesteal, h.pVision,
            h.pPotion, h.pGold, h.pXp, lootLuck, legacyName ?: "", legacyDepth,
            skills, item(h.weapon), item(h.armor), item(h.trinket), cds,
            boonQueue.joinToString("\u0002"), chronicle.personaTitle ?: "", tals
        ).joinToString("\u0001").let { "$RUN_SCHEMA\u0000$it" }
    }

    fun restoreRun(raw: String): Boolean {
        val dot = raw.indexOf('\u0000')
        if (dot <= 0 || raw.substring(0, dot).toIntOrNull() != RUN_SCHEMA) return false
        val f = raw.substring(dot + 1).split("\u0001")
        if (f.size < 33) return false
        return try {
            heroName = f[0]
            personality = Personality(Temperament.valueOf(f[1]), Focus.valueOf(f[2]))
            depth = f[3].toInt()
            val h = Hero(0, 0)
            turns = f[4].toInt()
            h.level = f[5].toInt(); h.xp = f[6].toInt(); h.xpNext = f[7].toInt()
            h.maxHp = f[8].toInt(); h.hp = f[9].toInt()
            h.atk = f[10].toInt(); h.def = f[11].toInt()
            h.potions = f[12].toInt(); h.gold = f[13].toInt(); h.kills = f[14].toInt()
            h.pCrit = f[15].toInt(); h.pThorns = f[16].toInt(); h.pLifesteal = f[17].toInt()
            h.pVision = f[18].toInt(); h.pPotion = f[19].toInt()
            h.pGold = f[20].toInt(); h.pXp = f[21].toInt()
            hero = h
            lootLuck = f[22].toInt()
            legacyName = f[23].ifEmpty { null }; legacyDepth = f[24].toInt()
            if (f[25].isNotEmpty()) for (ss in f[25].split("\u0002")) {
                val p = ss.split("\u0003")
                if (p.size < 19) continue
                hero.skills.add(Skill(p[0], p[1], School.entries[p[2].toInt()],
                    p[3].toInt(), SkillKind.entries[p[4].toInt()],
                    SkillFx.entries[p[5].toInt()], p[6].toInt(), p[7].toInt(), p[8].toInt(),
                    pAtk = p[9].toInt(), pDef = p[10].toInt(), pHp = p[11].toInt(),
                    pCrit = p[12].toInt(), pThorns = p[13].toInt(), pLifesteal = p[14].toInt(),
                    pVision = p[15].toInt(), pPotion = p[16].toInt(),
                    pGold = p[17].toInt(), pXp = p[18].toInt()))
            }
            fun item(si: String): Item? = if (si.isEmpty()) null else {
                val p = si.split("\u0003")
                Item(p[0], ItemSlot.entries[p[1].toInt()], p[2][0],
                     Rarity.entries[p[3].toInt()], p[4].toInt(), p[5].toInt(), p[6].toInt(),
                     p[7].toInt(), p[8].toInt(), p[9].toInt(), p[10].toInt() == 1,
                     p[11].toInt(), p[12].toInt(), p[13].toInt(), p[14].toInt(),
                     p[15].toInt(), p[16].toInt(), p[17].toInt(), p[18])
            }
            hero.weapon = item(f[26]); hero.armor = item(f[27]); hero.trinket = item(f[28])
            if (f[29].isNotEmpty()) for (cc in f[29].split("\u0002")) {
                val p = cc.split("\u0003")
                val v = p.getOrNull(1)?.toIntOrNull() ?: continue
                hero.cooldowns[p[0]] = v
            }
            if (f[30].isNotEmpty()) f[30].split("\u0002").forEach { boonQueue.add(it) }
            restoreAppliedTalents(if (f[32].isNotEmpty())
                f[32].split("\u0002").mapNotNull { pair ->
                    val kv = pair.split("\u0003")
                    kv.getOrNull(1)?.toIntOrNull()?.let { kv[0] to it }
                }.toMap() else emptyMap())
            chronicle.reset()
            chronicle.personaTitle = f[31].ifEmpty { null }
            chronicle.add(0, depth, "start", "$heroName relights the torch on floor $depth")
            chapterCount = (depth + 2) / 3
            lastBiomeId = Biomes.forDepth(depth).id
            seenTypes.clear(); journal.clear(); metNpcKinds.clear()
            said50 = false; said25 = false; said12 = false
            killStreak = 0; tauntCd = 0; ambientCd = 30
            heroAlive = true
            heroPoison = 0; darkKill = false
            mood = Mood.DELIBERATE; fightLog.clear()
            lastMdpAction = -1; lastMdpState = -1
            verdictReason = null
            featEvents.clear(); runStats.clear()
            nearDeathSaid = false
            wardenHunting = false; turnsThisFloor = 0; bribedThisFloor = false
            familiar = null; familiarHid = false
            genFloor()
            true
        } catch (e: Exception) { false }
    }

    // ---------- helpers ----------

    private fun exploredFraction(): Float {
        var total = 0; var seen = 0
        for (y in 0 until H) for (x in 0 until W) {
            if (map[y][x] == Tile.WALL) continue
            total++
            if (explored[y][x]) seen++
        }
        return if (total == 0) 1f else seen.toFloat() / total
    }

    private fun nearestKnownItem(slot: ItemSlot, dist: IntArray): FloorItem? {
        var best: FloorItem? = null
        var bestD = Int.MAX_VALUE
        for (fl in items) {
            if (!knownItems.contains(key(fl.x, fl.y))) continue
            if (fl.item.slot != slot) continue
            val d = dist[key(fl.x, fl.y)].let { if (it < 0) Int.MAX_VALUE else it }
            if (d < bestD) { bestD = d; best = fl }
        }
        return best
    }

    private fun adjacentMonster(): Monster? =
        monsters.firstOrNull { it.alive && manhattan(it.x, it.y, hero.x, hero.y) == 1 }

    fun monsterAt(x: Int, y: Int): Monster? =
        monsters.firstOrNull { it.alive && it.x == x && it.y == y }

    fun npcAt(x: Int, y: Int): Npc? =
        npcs.firstOrNull { it.x == x && it.y == y }

    fun key(x: Int, y: Int) = y * W + x
    private fun decode(k: Int) = Pt(k % W, k / W)
    private fun manhattan(x1: Int, y1: Int, x2: Int, y2: Int) = abs(x1 - x2) + abs(y1 - y2)
    private fun sgn(v: Int) = if (v > 0) 1 else if (v < 0) -1 else 0
}
