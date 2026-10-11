package com.focusrealm.everdeep

import android.content.Context
import android.content.SharedPreferences
import com.focusrealm.everdeep.game.Feats
import com.focusrealm.everdeep.game.Focus
import com.focusrealm.everdeep.game.GenePool
import com.focusrealm.everdeep.game.Hero
import com.focusrealm.everdeep.game.Legacy
import com.focusrealm.everdeep.game.PersonalityForge
import com.focusrealm.everdeep.game.Rarity
import com.focusrealm.everdeep.game.RunSummary
import com.focusrealm.everdeep.game.SkillKind
import com.focusrealm.everdeep.game.Skills
import com.focusrealm.everdeep.game.Talent
import com.focusrealm.everdeep.game.Temperament
import com.focusrealm.everdeep.game.Tone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

object GameHost {

    enum class State { IDLE, FOCUSED, PAUSED, FALLEN }
    data class StatusUi(val state: State, val appLabel: String)
    data class LogLine(val turn: Int, val text: String, val tone: Tone, val n: Int = 1)

    data class HeroUi(
        val name: String, val lineage: Int, val depth: Int, val level: Int,
        val hp: Int, val maxHp: Int, val atk: Int, val def: Int,
        val xp: Int, val xpNext: Int, val potions: Int, val gold: Int,
        val kills: Int, val turns: Int,
        val biomeName: String = "", val biomeIcon: String = "",
        val avatar: String = "@", val persona: String = ""
    )

    data class FallenUi(val name: String, val lineage: Int, val depth: Int, val level: Int,
                        val kills: Int, val gold: Int, val turns: Int, val slainBy: String,
                        val epitaph: String = "", val avatar: String = "@",
                        val persona: String = "")

    data class SkillUi(val name: String, val emoji: String, val cd: Int, val cdMax: Int,
                       val active: Boolean)

    data class Records(val bestDepth: Int = 0, val heroesFallen: Int = 0,
                       val totalKills: Int = 0, val totalFloors: Int = 0,
                       val lessons: Int = 0, val generation: Int = 0)

    data class RivalRecord(val name: String, val glyph: String, val best: Int, val alive: Boolean)
    data class VowUi(val name: String, val desc: String, val progress: Int,
                     val goal: Int, val unit: String, val done: Boolean, val boon: String)

    // ---------- flows ----------
    val heroUi = MutableStateFlow(HeroUi("—", 0, 1, 1, 24, 24, 4, 1, 0, 20, 1, 0, 0, 0))
    val status = MutableStateFlow(StatusUi(State.IDLE, ""))
    val log = MutableStateFlow<List<LogLine>>(emptyList())
    val recordsFlow = MutableStateFlow(Records())
    val sessionSeconds = MutableStateFlow(0)
    val running = MutableStateFlow(false)
    val speed = MutableStateFlow(1)
    val skillsUi = MutableStateFlow<List<SkillUi>>(emptyList())
    val sagaFlow = MutableStateFlow<List<String>>(emptyList())
    val playing = MutableStateFlow(false)
    val fallenRun = MutableStateFlow<FallenUi?>(null)
    val hall = MutableStateFlow<List<RivalRecord>>(emptyList())
    val standings = MutableStateFlow("")
    val vowsFlow = MutableStateFlow<List<VowUi>>(emptyList())
    val embersFlow = MutableStateFlow(0)
    val talentsFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ledgerFlow = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val streakFlow = MutableStateFlow(0)
    val curveFlow = MutableStateFlow<List<Float>>(emptyList())
    val sessionGoal = MutableStateFlow(0)
    val sessionElapsed = MutableStateFlow(0)
    val auraTier = MutableStateFlow(0)
    val fallenHall = MutableStateFlow<List<String>>(emptyList())
    val featsDone = HashSet<String>()

    var playbackSpeedMs = 260

    // ---------- threading ----------
    private val engineDispatcher = java.util.concurrent.Executors
        .newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private var loop: Job? = null
    private var pbJob: Job? = null
    private var stepFailures = 0

    private lateinit var engine: Engine
    private var appCtx: Context? = null
    private var prefs: SharedPreferences? = null

    // ---------- identity ----------
    var heroAvatar: String = "@"
    private var customName: String? = null
    var identityChosen = false
        private set

    // ---------- lineage state ----------
    private val personaScores = HashMap<String, Float>()
    private val pool = GenePool(GenePool.defaultBounds())
    private var heroCount = 0
    private var records = Records()
    private var sessionStart = 0L
    private var fallenAt = 0L
    private var wasPaused = false
    private var lastFallenName: String? = null
    private var lastFallenDepth = 0

    // codex
    private val codexSeen = HashSet<String>()
    private val codexKills = HashMap<String, Int>()
    private val codexMet = HashSet<String>()
    private val codexBiomes = HashSet<String>()
    private val codexItems = LinkedHashSet<String>()
    fun codexIsSeen(id: String) = codexSeen.contains(id)
    fun codexKillCount(id: String) = codexKills[id] ?: 0
    fun codexMetKinds(): Set<String> = codexMet
    fun codexBiomesSeen(): Set<String> = codexBiomes
    fun codexItemNames(): List<String> = codexItems.toList()

    // history
    private val history = ArrayList<LogLine>()
    private val histLock = Any()

    // ledger / time
    private val distractionMs = HashMap<String, Long>()
    private val hourMs = HashMap<Int, Long>()
    private val focusDays = HashSet<String>()
    private var curve = ArrayList<Float>()
    private var focusSecToday = 0L
    private var continuousFocusMs = 0L
    private var tier4Paid = false
    private var lastStepTime = 0L
    private var lastLedgerKey = ""
    private var lastLedgerSave = 0L
    private var lastDayCheck = 0L

    // vows
    private var vowDay = ""
    private var vows: List<Legacy.Vow> = emptyList()
    private val vowProgress = HashMap<String, Int>()
    private val vowDone = HashSet<String>()
    private var lastKills = 0

    // sessions
    private var sessionElapsedMs = 0L
    private var wardArmed = false

    // pause telemetry
    private var lastTickerAt = 0L
    private var lastPingAt = 0L
    private var stipendAt = 0L
    private var darkCount = 0
    private var darkWarned = false
    private var pauseStart = 0L
    private var woundedNotified = false
    private var runEarned = 0; private var runSpent = 0; private var lastGold = 0
    private var lastW1 = ""; private var lastW2 = ""

    // tips
    private val tips = listOf(
        25 to "🧭 the hero tunes herself every 50 turns — watch for 🧠 lessons",
        80 to "📜 the scroll button reads this run's chronicle — and replays it",
        180 to "🛡 arm the Focus Shield in the blocklist panel: blocked apps must ask twice",
        320 to "⏳ when the hero waits, the pause notification offers a one-tap block",
        520 to "🔥 embers buy permanent talents — wardens pay 3, every floor pays 1")
    private var tipIdx = Int.MAX_VALUE

    val isReady get() = ::engine.isInitialized
    fun engineRef(): Engine? = if (::engine.isInitialized) engine else null

    fun setIdentity(name: String, avatar: String) {
        if (!identityChosen) heroCount = maxOf(0, heroCount - 1)
        customName = name.trim().take(24).ifEmpty { null }
        heroAvatar = avatar
        identityChosen = true
        prefs?.edit()
            ?.putString("heroName", customName)
            ?.putString("heroAvatar", heroAvatar)
            ?.putBoolean("identityChosen", true)
            ?.putInt("heroCount", heroCount)
            ?.apply()
    }

    fun randomHeroName(): String = heroName()

    private fun heroName(): String {
        val first = listOf("Mora","Kael","Vesper","Ivo","Sable","Rook","Anouk","Tamar",
                           "Brann","Lyra","Orsik","Nell","Cassian","Yara","Fenn","Odile")
        val epi = listOf("the Unbowed","of the Lantern","Nine-Fingers","the Quiet","Ashwalker",
                         "of the Ninth Gate","the Patient","Emberborn","Deepsworn",
                         "Half-Boots","the Long-Waiting","Iron-Tooth","the Merciful","Oathless")
        return "${first.random()} ${epi.random()}"
    }

    // ================= init =================

    fun init(context: Context) {
        if (isReady) return
        appCtx = context.applicationContext
        SoundCues.init(context.applicationContext)
        prefs = appCtx!!.getSharedPreferences("everdeep", Context.MODE_PRIVATE)
        val p = prefs!!
        records = Records(p.getInt("bestDepth", 0), p.getInt("heroesFallen", 0),
                          p.getInt("totalKills", 0), p.getInt("totalFloors", 0),
                          p.getInt("lessons", 0), p.getInt("generation", 0))
        recordsFlow.value = records
        heroCount = p.getInt("heroCount", 0)
        heroAvatar = p.getString("heroAvatar", "@") ?: "@"
        customName = p.getString("heroName", null)
        identityChosen = p.getBoolean("identityChosen", false)
        buildEngine()
        engine.brain.onPersist = { s -> prefs?.edit()?.putString("brain", s)?.apply() }
        prefs?.getString("brain", null)?.let { engine.brain.load(it) }
        prefs?.getString("lore", null)?.let { engine.lore.load(it) }
        prefs?.getString("genePool", null)?.let { pool.load(it) }
        prefs?.getString("mdp", null)?.let { engine.mdp.load(it) }
        engine.mctsBudget = (150 / speed.value).coerceIn(40, 150)
        records = records.copy(generation = pool.generation)
        recordsFlow.value = records
        prefs?.getString("personaScores", null)?.split(";")?.forEach { pair ->
            val kv = pair.split("=", limit = 2)
            kv.getOrNull(1)?.toFloatOrNull()?.let { personaScores[kv[0]] = it }
        }
        embersFlow.value = p.getInt("embers", 0)
        talentsFlow.value = p.getStringSet("talents", emptySet())?.mapNotNull {
            val kv = it.split("=")
            kv.getOrNull(1)?.toIntOrNull()?.let { r -> kv[0] to r }
        }?.toMap() ?: emptyMap()
        featsDone.addAll(p.getStringSet("feats", emptySet()) ?: emptySet())
        codexSeen.addAll(p.getString("codexSeen", null)
            ?.split(";")?.filter { s -> s.isNotBlank() } ?: emptyList())
        p.getString("codexKills", null)?.split(";")?.forEach { pair ->
            val kv = pair.split("=", limit = 2)
            kv.getOrNull(1)?.toIntOrNull()?.let { codexKills[kv[0]] = it }
        }
        codexMet.addAll(p.getStringSet("codexMet", emptySet()) ?: emptySet())
        codexBiomes.addAll(p.getStringSet("codexBiomes", emptySet()) ?: emptySet())
        codexItems.addAll(p.getString("codexItems", null)?.split(";") ?: emptyList())
        p.getString("distraction", null)?.split(";")?.forEach { pair ->
            val kv = pair.split("=", limit = 2)
            kv.getOrNull(1)?.toLongOrNull()?.let { distractionMs[kv[0]] = it }
        }
        p.getString("hourMs", null)?.split(";")?.forEach { pair ->
            val kv = pair.split("=", limit = 2)
            kv.getOrNull(0)?.toIntOrNull()?.let { h ->
                kv.getOrNull(1)?.toLongOrNull()?.let { v -> hourMs[h] = v }
            }
        }
        focusDays.addAll(p.getStringSet("focusDays", emptySet()) ?: emptySet())
        curve.addAll(p.getString("curve", null)?.split(",")
            ?.mapNotNull { it.toFloatOrNull() } ?: emptyList())
        curveFlow.value = curve
        fallenHall.value = p.getString("fallenHall", null)
            ?.split("\u0001")?.filter { it.isNotBlank() } ?: emptyList()
        hall.value = p.getString("hall", null)?.split(";")?.mapNotNull {
            val p2 = it.split("~")
            if (p2.size == 4) RivalRecord(p2[0], p2[1], p2[2].toIntOrNull() ?: 0, p2[3] == "1") else null
        } ?: emptyList()
        sagaFlow.value = p.getString("lastSaga", null)
            ?.split("\u0001")?.filter { it.isNotBlank() } ?: emptyList()
        // resume a run interrupted by process death — or begin fresh
        val snap = p.getString("run", null)
        if (snap != null && identityChosen && engine.restoreRun(snap)) {
            engine.applyTalents(talentsFlow.value)
            addLog("🕯️ the torch relights — ${engine.heroName} resumes on floor ${engine.depth}", Tone.EPIC)
            val gone = System.currentTimeMillis() - p.getLong("lastSeen", 0L)
            if (gone > 120_000) {
                val mins = ((gone / 60_000).coerceIn(1, 30)).toInt()
                repeat(mins) { engine.rivalTick() }
                addLog(Narrator.catchUp(mins.toLong()), Tone.PLAIN)
            }
        } else {
            newHero()
        }
        vowDay = p.getString("vowDay", "") ?: ""
        streakFlow.value = computeStreak()
        ensureVows()
        scheduleReport()
    }

    private fun buildEngine() {
        engine = Engine()
        engine.bridge = object : Engine.Bridge {
            override fun log(text: String, tone: Tone) = addLog(text, tone)
            override fun onDepth(depth: Int) {
                records = records.copy(totalFloors = records.totalFloors + 1)
                if (depth > records.bestDepth) records = records.copy(bestDepth = depth)
                saveRecords()
                embersFlow.value = embersFlow.value + 1
                prefs?.edit()?.putInt("embers", embersFlow.value)?.apply()
                vowTick("floor")
                if (codexBiomes.add(engine.biome.id)) saveCodex()
                saveRun()
                SoundCues.descend()
                hall.value.firstOrNull { it.alive }?.let {
                    if (depth == it.best + 1) addLog("🥇 ${Narrator.drawAhead(it.name)}", Tone.EPIC)
                }
                publishStandings()
                KeepAliveService.instance?.update(
                    "${engine.biome.icon} ${engine.biome.name} · floor $depth · ${engine.hero.kills} kills")
            }
            override fun onLevelUp(level: Int) { vowTick("level"); SoundCues.level() }
            override fun onFallen(s: RunSummary) {
                records = records.copy(heroesFallen = records.heroesFallen + 1,
                                       totalKills = records.totalKills + s.kills)
                if (s.depth > records.bestDepth) records = records.copy(bestDepth = s.depth)
                saveRecords()
                fallenAt = System.currentTimeMillis()
                status.value = StatusUi(State.FALLEN, "")
                addLog("the run ends — floor ${s.depth}, level ${s.level}, ${s.kills} kills, ${s.gold} gold", Tone.BAD)
                fallenRun.value = FallenUi(engine.heroName, heroCount, s.depth, s.level,
                                           s.kills, s.gold, s.turns, s.slainBy, s.epitaph,
                                           heroAvatar, engine.personality.title)
                // the lineage remembers which temperaments survive
                val perf = s.depth + s.level * 0.5f
                val key = engine.personality.temperament.name
                personaScores[key] = (personaScores[key] ?: 3f) * 0.6f + perf * 0.4f
                prefs?.edit()?.putString("personaScores",
                    personaScores.entries.joinToString(";") { "${it.key}=${it.value}" })?.apply()
                // Lamarckian fold-back: the learned brain becomes the hereditary line
                engine.genome?.let { g ->
                    val fitness = s.depth * 10f + s.kills * 1.5f + s.level * 4f + s.gold / 40f
                    g.values.clear()
                    g.values.putAll(brainValues(engine.brain.serialize()))
                    pool.submit(g, fitness)
                    prefs?.edit()?.putString("genePool", pool.serialize())?.apply()
                    addLog("🧬 ${Narrator.genomeScored(fitness.toInt())} — generation ${pool.generation}", Tone.PLAIN)
                    records = records.copy(generation = pool.generation)
                    recordsFlow.value = records
                    curve.add(fitness)
                    if (curve.size > 24) curve.removeAt(0)
                    curveFlow.value = curve
                    prefs?.edit()?.putString("curve", curve.joinToString(","))?.apply()
                }
                lastFallenName = engine.heroName
                lastFallenDepth = s.depth
                val saga = engine.buildSaga(s)
                sagaFlow.value = saga
                prefs?.edit()?.putString("lastSaga", saga.joinToString("\u0001"))?.apply()
                addLog("📜 the saga of ${engine.heroName} is written — tap 📜 to relive it", Tone.EPIC)
                val line = "$heroAvatar ${engine.heroName} — floor ${s.depth} · \"${s.epitaph}\""
                val cur = ArrayList(listOf(line)); cur.addAll(fallenHall.value.take(11))
                fallenHall.value = cur
                prefs?.edit()?.putString("fallenHall", cur.joinToString("\u0001"))?.apply()
                val st = engine.runStats
                Narrator.recapLines(st["dealt"] ?: 0, st["taken"] ?: 0, st["bigHit"] ?: 0,
                    st["bigTaken"] ?: 0, st["potions"] ?: 0, st["skills"] ?: 0,
                    runEarned, runSpent).forEach { addLog(it, Tone.PLAIN) }
                clearRun()
                SoundCues.death()
                widget("⚰️ ${engine.heroName} — floor ${s.depth}", "the lineage remembers")
                KeepAliveService.instance?.update("⚰️ The hero fell on floor ${s.depth}. A successor comes…")
            }
            override fun onTurnEnd(hero: Hero, depth: Int, turns: Int) {
                heroUi.value = HeroUi(engine.heroName, heroCount, depth, hero.level,
                    hero.hp, hero.maxHp, hero.atk, hero.def, hero.xp, hero.xpNext,
                    hero.potions, hero.gold, hero.kills, turns,
                    engine.biome.name, engine.biome.icon, heroAvatar, engine.personality.title)
                skillsUi.value = engine.hero.skills.map {
                    SkillUi(it.name, Skills.emoji(it.school),
                            engine.hero.cooldowns[it.id] ?: 0, it.cd,
                            it.kind == SkillKind.ACTIVE)
                }
                publishStandings()
                val g = hero.gold
                if (g > lastGold) runEarned += g - lastGold
                else if (g < lastGold) runSpent += lastGold - g
                lastGold = g
                if (hero.kills > lastKills) {
                    vowTick("kill", hero.kills - lastKills)
                    lastKills = hero.kills
                }
                val wounded = engine.heroAlive && hero.hp * 4 < hero.maxHp
                if (wounded && !woundedNotified) {
                    woundedNotified = true
                    KeepAliveService.instance?.update(
                        "🩸 ${engine.heroName} is bleeding on floor ${engine.depth}")
                } else if (!wounded) woundedNotified = false
                if (turns % 20 == 0) saveRun()
                if (turns % 20 == 0) {
                    prefs?.edit()?.putString("lore", engine.lore.serialize())?.apply()
                    prefs?.edit()?.putString("mdp", engine.mdp.serialize())?.apply()
                }
                ensureVows()
                checkFeats()
                publishLedger()
                widget("$heroAvatar ${engine.heroName} · floor ${engine.depth}",
                    (if (FocusBus.isBlockedNow()) "🚫 the hero waits" else "🟢 focused") +
                    " · ⚔️ ${hero.kills} · T${turns}")
                if (tipIdx < tips.size && turns >= tips[tipIdx].first) {
                    addLog(tips[tipIdx].second, Tone.PLAIN)
                    tipIdx++
                }
            }
            override fun onLesson(text: String) {
                addLog(text, Tone.EPIC)
                records = records.copy(lessons = records.lessons + 1)
                saveRecords()
            }
            override fun onRivalEvent(name: String, glyph: Char, depth: Int, dead: Boolean) {
                val cur = hall.value.toMutableList()
                val i = cur.indexOfFirst { it.name == name }
                if (i >= 0) cur[i] = RivalRecord(name, glyph.toString(),
                    maxOf(depth, cur[i].best), cur[i].alive && !dead)
                else cur.add(RivalRecord(name, glyph.toString(), depth, !dead))
                hall.value = cur.sortedByDescending { it.best }.take(8)
                prefs?.edit()?.putString("hall",
                    hall.value.joinToString(";") {
                        "${it.name}~${it.glyph}~${it.best}~${if (it.alive) 1 else 0}" })?.apply()
                publishStandings()
            }
            override fun onEmber(n: Int) {
                embersFlow.value = embersFlow.value + n
                prefs?.edit()?.putInt("embers", embersFlow.value)?.apply()
            }
            override fun onDiscoverMonster(id: String) { if (codexSeen.add(id)) saveCodex() }
            override fun onMonsterKilled(id: String) {
                codexKills[id] = (codexKills[id] ?: 0) + 1
                saveCodex()
            }
            override fun onDiscoverNpc(kind: String) { if (codexMet.add(kind)) saveCodex() }
            override fun onDiscoverItem(name: String) {
                if (codexItems.add(name)) {
                    while (codexItems.size > 60) codexItems.remove(codexItems.first())
                    saveCodex()
                }
            }
        }
        newHero()
    }

    private fun newHero() {
        stopPlayback()
        clearRun()
        runEarned = 0; runSpent = 0; lastGold = 0
        tipIdx = if (heroCount <= 2) 0 else Int.MAX_VALUE
        lastKills = 0
        val genome = pool.spawn()
        engine.inherit(genome)
        engine.personality = PersonalityForge.forge(personaScores)
        val birth = Narrator.genomeBirth(genome.origin,
            pool.population.firstOrNull()?.describe() ?: "the first line")
        records = records.copy(generation = pool.generation)
        recordsFlow.value = records
        heroCount++
        prefs?.edit()?.putInt("heroCount", heroCount)?.apply()
        engine.setLegacy(lastFallenName, lastFallenDepth)
        engine.newRun(customName?.takeIf { it.isNotBlank() } ?: heroName())
        engine.applyTalents(talentsFlow.value)
        addLog("$heroAvatar №$heroCount — ${engine.heroName} enters the Everdeep", Tone.EPIC)
        addLog("🧬 $birth — generation ${pool.generation}", Tone.EPIC)
        fallenAt = 0
        fallenRun.value = null
        wasPaused = false
        woundedNotified = false
        status.value = if (running.value) StatusUi(State.FOCUSED, currentLabel())
                       else StatusUi(State.IDLE, "")
    }

    // ================= session =================

    fun startSession() {
        if (running.value || !isReady) return
        running.value = true
        sessionStart = System.currentTimeMillis()
        sessionSeconds.value = 0
        appCtx?.let { KeepAliveService.start(it); FocusTile.sync(it) }
        loop = scope.launch {
            while (isActive) {
                delay(tickMs())
                try {
                    step()
                    stepFailures = 0
                } catch (t: Throwable) {
                    stepFailures++
                    android.util.Log.w("Everdeep", "sim step failed", t)
                    if (stepFailures >= 5) {
                        addLog("the torch gutters — the deep rests a while", Tone.BAD)
                        stopSession()
                    }
                }
            }
        }
    }

    fun stopSession() {
        stopPlayback()
        loop?.cancel(); loop = null
        running.value = false
        status.value = StatusUi(State.IDLE, "")
        appCtx?.let { KeepAliveService.stop(it); FocusTile.sync(it) }
    }

    fun restartRun() { scope.launch { if (isReady) newHero() } }

    fun setSpeed(s: Int) {
        speed.value = s
        if (isReady) engine.mctsBudget = (150 / s).coerceIn(40, 150)
    }

    var activityVisible = true
    private fun tickMs(): Long {
        val base = 800L / speed.value
        return if (!activityVisible && isReady && engine.calm()) base * 2 else base
    }

    private fun currentLabel(): String {
        val pkg = FocusBus.currentApp.value ?: return "your device"
        return when {
            !FocusBus.monitorActive -> "no monitor"
            pkg == appCtx?.packageName -> "Everdeep"
            else -> FocusBus.labelFor(pkg)
        }
    }

    // ================= the tick =================

    private fun step() {
        if (!isReady) return
        val now = System.currentTimeMillis()
        val delta = if (lastStepTime > 0 && running.value) now - lastStepTime else 0L
        if (delta in 1..10_000) {
            if (FocusBus.isBlockedNow()) {
                FocusBus.currentApp.value?.let {
                    distractionMs[it] = (distractionMs[it] ?: 0L) + delta
                }
                val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                hourMs[hr] = (hourMs[hr] ?: 0L) + delta
            } else {
                focusSecToday += delta
                continuousFocusMs += delta
                if (sessionGoal.value > 0) sessionElapsedMs += delta
            }
        }
        lastStepTime = now
        while (focusSecToday >= 60_000) { focusSecToday -= 60_000; vowTick("min") }
        while (sessionGoal.value > 0 && sessionElapsedMs >= 60_000) {
            sessionElapsedMs -= 60_000
            sessionElapsed.value = sessionElapsed.value + 1
            KeepAliveService.instance?.progress(sessionElapsed.value, sessionGoal.value * 60)
        }
        if (sessionGoal.value > 0 && sessionElapsed.value >= sessionGoal.value * 60) completeSession()
        val nt = tierFor(continuousFocusMs)
        if (nt != auraTier.value) applyTier(nt)

        // warded hours — the app enforces its own schedule
        val ward = FocusBus.inWardWindow()
        if (ward && !wardArmed) {
            wardArmed = true
            if (!FocusBus.shield) {
                FocusBus.setShield(true)
                addLog("⏰ warded hours — the shield raises itself", Tone.PLAIN)
            }
            if (!running.value) startSession()
        } else if (!ward) wardArmed = false

        if (playing.value) return                 // replay holds the stage

        // memorial beat, then the next hero takes the torch
        if (!engine.heroAlive) {
            engine.rivalTick()                    // rivals delve past the tomb
            if (fallenAt > 0 && now - fallenAt > 3500) newHero()
            return
        }
        sessionSeconds.value = ((now - sessionStart) / 1000).toInt()

        // the whole point: blocked app/site on screen → the hero freezes
        if (FocusBus.isBlockedNow()) {
            if (!wasPaused) {
                wasPaused = true
                pauseStart = now
                lastTickerAt = now; lastPingAt = now; stipendAt = now
                darkWarned = false
                SoundCues.distract()
                val what = FocusBus.siteHit.value?.let { "🌐 $it" }
                    ?: FocusBus.currentApp.value?.let { FocusBus.labelFor(it) } ?: "your device"
                KeepAliveService.instance?.update("⏸ the hero waits — $what",
                    FocusBus.currentApp.value)
                status.value = StatusUi(State.PAUSED, what)
                addLog("the hero freezes — $what is on your screen", Tone.BAD)
            }
            if (now - lastTickerAt >= 30_000) {
                lastTickerAt = now
                val s = (now - pauseStart) / 1000
                val lead = hall.value.firstOrNull { it.alive }
                val leadTxt = if (lead != null && lead.best > engine.depth)
                    " · ${lead.glyph} ${lead.name} ${lead.best - engine.depth} floors ahead" else ""
                KeepAliveService.instance?.update(
                    "⏸ %d:%02d lost — the hero waits%s".format(s / 60, s % 60, leadTxt),
                    FocusBus.currentApp.value)
                saveLedgerPrefs()
            }
            if (now - lastPingAt >= 120_000) { lastPingAt = now; SoundCues.distract() }
            repeat(3) { engine.rivalTick() }      // rivals feed on your absence
            if (now - stipendAt >= 300_000) {
                stipendAt = now
                engine.rivalStipend()
                if ((0..99).random() < 50) addLog(Narrator.stipend(), Tone.BAD)
            }
            if (FocusBus.darkAdvances) {
                if (!darkWarned && engine.monsters.any { it.alive && it.aggro && !it.bribed }) {
                    darkWarned = true
                    addLog(Narrator.darkWarning(), Tone.BAD)
                }
                if (++darkCount % 4 == 0) engine.darkAdvancesTick()
            }
            return
        }
        if (wasPaused) {
            wasPaused = false
            val gone = now - pauseStart
            if (gone <= 30_000) vowTick("resist")
            if (gone > 60_000) dropAura()
            KeepAliveService.instance?.update(
                "${engine.biome.icon} ${engine.biome.name} · floor ${engine.depth} · ${engine.hero.kills} kills")
            status.value = StatusUi(State.FOCUSED, currentLabel())
            addLog("the hero presses on", Tone.PLAIN)
        }
        if (status.value.state == State.IDLE) status.value = StatusUi(State.FOCUSED, currentLabel())

        engine.heroTurn()
    }

    // ---------- aura tiers ----------

    private fun tierFor(ms: Long): Int = when {
        ms >= 60 * 60_000L -> 4
        ms >= 30 * 60_000L -> 3
        ms >= 15 * 60_000L -> 2
        ms >= 5 * 60_000L  -> 1
        else -> 0
    }

    private fun applyTier(t: Int) {
        auraTier.value = t
        if (!isReady) return
        engine.auraXp = when (t) { 1 -> 0.25f; 2 -> 0.5f; 3, 4 -> 1f; else -> 0f }
        engine.auraVision = when (t) { 2, 3 -> 1; 4 -> 2; else -> 0 }
        if (t >= 1) {
            addLog(Narrator.auraUp(t), Tone.EPIC)
            if (t == 4 && !tier4Paid) {
                tier4Paid = true
                engine.boonQueue.add("RELIC")
                KeepAliveService.instance?.update("✨ Transcendent — a relic waits on the next stair")
            }
        }
    }

    private fun dropAura() {
        val t = auraTier.value
        if (t <= 0) return
        val nt = (t - 2).coerceAtLeast(0)
        continuousFocusMs = listOf(0L, 5, 15, 30, 60)[nt] * 60_000L
        if (nt == 0) tier4Paid = false
        applyTier(nt)
        addLog(Narrator.auraDrop(), Tone.BAD)
    }

    // ---------- sessions ----------

    fun setGoal(min: Int) {
        val start = min > 0 && min != sessionGoal.value
        sessionGoal.value = if (start) min else 0
        sessionElapsedMs = 0; sessionElapsed.value = 0
        if (!start) KeepAliveService.instance?.progress(0, 0)
        if (start) {
            addLog("✨ a $min-minute session begins — ${Narrator.auraKindle()}", Tone.EPIC)
            SoundCues.session()
        }
    }

    private fun completeSession() {
        sessionGoal.value = 0; sessionElapsedMs = 0; sessionElapsed.value = 0
        KeepAliveService.instance?.progress(0, 0)
        embersFlow.value = embersFlow.value + 3
        prefs?.edit()?.putInt("embers", embersFlow.value)?.apply()
        if (isReady) engine.boonQueue.add("RELIC")
        addLog(Narrator.sessionDone(), Tone.EPIC)
        KeepAliveService.instance?.update("🎯 Session complete — a relic waits on the next stair")
        SoundCues.session()
    }

    fun buyTalent(id: String): Boolean {
        val t = Talent.entries.firstOrNull { it.id == id } ?: return false
        val cur = talentsFlow.value[id] ?: 0
        if (cur >= t.maxRank) return false
        val cost = t.cost(cur)
        if (embersFlow.value < cost) return false
        embersFlow.value -= cost
        talentsFlow.value = talentsFlow.value + (id to (cur + 1))
        prefs?.edit()?.putInt("embers", embersFlow.value)?.apply()
        prefs?.edit()?.putStringSet("talents",
            talentsFlow.value.entries.map { "${it.key}=${it.value}" }.toSet())?.apply()
        if (isReady) scope.launch { engine.applyTalentDelta(id, 1) }
        return true
    }

    // ================= vows =================

    private fun ensureVows() {
        val now = System.currentTimeMillis()
        if (now - lastDayCheck < 30_000) return
        lastDayCheck = now
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date(now))
        if (today != vowDay) {
            if (vowDay.isNotEmpty() && focusSecToday >= 10 * 60_000) focusDays.add(vowDay)
            prefs?.edit()?.putStringSet("focusDays", focusDays)?.apply()
            focusSecToday = 0
            streakFlow.value = computeStreak()
            vowDay = today
            vows = Legacy.rollFor(today)
            vowProgress.clear(); vowDone.clear()
            if (isReady) lastKills = engine.hero.kills
            prefs?.edit()?.putString("vowDay", today)?.apply()
        }
        publishVows()
    }

    private fun publishVows() {
        vowsFlow.value = vows.map { v ->
            VowUi(v.name, v.desc, (vowProgress[v.id] ?: 0).coerceAtMost(v.goal),
                  v.goal, v.unit, vowDone.contains(v.id), v.boon)
        }
    }

    private fun vowTick(kind: String, amount: Int = 1) {
        var changed = false
        for (v in vows) {
            if (vowDone.contains(v.id) || v.unit != kind) continue
            val np = (vowProgress[v.id] ?: 0) + amount
            vowProgress[v.id] = np
            changed = true
            if (np >= v.goal) {
                vowDone.add(v.id)
                if (isReady) engine.boonQueue.add(v.boon)
                addLog("🕯️ vow kept — ${v.name}. The deep will pay ${Legacy.boonName(v.boon)} on the next stair down.", Tone.EPIC)
                KeepAliveService.instance?.update("🕯️ Vow kept: ${v.name} — a boon waits below")
            }
        }
        if (changed) publishVows()
    }

    // ================= feats =================

    private fun checkFeats() {
        if (!isReady) return
        val gained = ArrayList<com.focusrealm.everdeep.game.Feat>()
        fun grant(id: String) {
            if (featsDone.add(id))
                Feats.ALL.firstOrNull { it.id == id }?.let { gained.add(it) }
        }
        while (engine.featEvents.isNotEmpty()) {
            val (ev, v) = engine.featEvents.removeAt(0)
            when (ev) {
                "kill" -> {
                    grant("blood")
                    if (records.totalKills + engine.hero.kills >= 100) grant("century")
                }
                "depth" -> {
                    if (v >= 5) grant("five")
                    if (v >= 10) grant("ten")
                    if (v >= 15) grant("fifteen")
                }
                "warden" -> grant("warden")
                "rival" -> grant("rival")
                "legend" -> grant("legend")
            }
        }
        if (engine.mdp.decisions >= 500) grant("scholar")
        if (records.generation >= 10) grant("gen10")
        if (gained.isNotEmpty()) {
            prefs?.edit()?.putStringSet("feats", featsDone.toSet())?.apply()
            var total = 0
            for (f in gained) {
                total += f.embers
                addLog("🏅 feat earned — ${f.name} (+${f.embers} 🔥)", Tone.EPIC)
            }
            embersFlow.value = embersFlow.value + total
            prefs?.edit()?.putInt("embers", embersFlow.value)?.apply()
        }
    }

    // ================= ledger =================

    private fun publishLedger() {
        val snap = distractionMs.entries.sortedByDescending { it.value }
            .take(6).map { FocusBus.labelFor(it.key) to it.value / 1000L }
        val key = snap.joinToString(",") + "#" + hourMs.hashCode()
        if (key != lastLedgerKey) {
            lastLedgerKey = key
            ledgerFlow.value = snap
        }
        val now = System.currentTimeMillis()
        if (now - lastLedgerSave > 60_000) {
            lastLedgerSave = now
            saveLedgerPrefs()
        }
    }

    private fun saveLedgerPrefs() {
        val e = prefs?.edit() ?: return
        e.putString("distraction",
            distractionMs.entries.joinToString(";") { "${it.key}=${it.value}" })
        e.putString("hourMs",
            hourMs.entries.joinToString(";") { "${it.key}=${it.value}" })
        e.apply()
    }

    fun heatmap(): List<Long> = (0..23).map { (hourMs[it] ?: 0L) / 60_000L }

    private fun computeStreak(): Int {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        var t = System.currentTimeMillis()
        if (!focusDays.contains(fmt.format(java.util.Date(t)))) t -= 86_400_000L
        var n = 0
        while (focusDays.contains(fmt.format(java.util.Date(t)))) { n++; t -= 86_400_000L }
        return n
    }

    // ================= history / playback =================

    private fun addLog(text: String, tone: Tone) {
        val t = if (isReady) engine.turns else 0
        val (line, bumped) = synchronized(histLock) {
            val hl = history.lastOrNull()
            if (hl != null && hl.text == text) {
                val b = hl.copy(n = hl.n + 1)
                history[history.size - 1] = b
                b to true
            } else {
                val nl = LogLine(t, text, tone)
                history.add(nl)
                if (history.size > 800) history.removeAt(0)
                nl to false
            }
        }
        if (speed.value < 4 || tone != Tone.PLAIN) {
            log.value = (if (bumped) log.value.dropLast(1) else log.value + line).takeLast(40)
        }
    }

    fun historySnapshot(): List<LogLine> = synchronized(histLock) { history.toList() }

    fun startPlayback() {
        if (historySnapshot().isEmpty() || playing.value) return
        pbJob = scope.launch {
            playing.value = true
            val snap = historySnapshot()
            for (i in snap.indices) {
                if (!playing.value) break
                log.value = snap.take(i + 1).takeLast(6)
                delay(playbackSpeedMs)
            }
            playing.value = false
        }
    }

    fun stopPlayback() {
        playing.value = false
        pbJob?.cancel(); pbJob = null
        if (isReady) log.value = historySnapshot().takeLast(6)
    }

    // ================= standings / widget =================

    private fun publishStandings() {
        val h = heroUi.value
        val top = hall.value.firstOrNull { it.alive }
        standings.value = when {
            top == null -> ""
            top.best > h.depth -> "⚔️ ${top.glyph} ${top.name} — floor ${top.best}, ${top.best - h.depth} below you"
            else -> "⚔️ ${top.glyph} ${top.name} — floor ${top.best} · you lead"
        }
    }

    private fun widget(l1: String, l2: String) {
        if (l1 == lastW1 && l2 == lastW2) return
        lastW1 = l1; lastW2 = l2
        try { EverdeepWidget.update(appCtx!!, l1, l2) } catch (_: Exception) {}
    }

    // ================= persistence =================

    private fun saveRecords() {
        recordsFlow.value = records
        prefs?.edit()
            ?.putInt("bestDepth", records.bestDepth)
            ?.putInt("heroesFallen", records.heroesFallen)
            ?.putInt("totalKills", records.totalKills)
            ?.putInt("totalFloors", records.totalFloors)
            ?.putInt("lessons", records.lessons)
            ?.putInt("generation", records.generation)
            ?.apply()
    }

    private fun saveRun() {
        if (!isReady || !engine.heroAlive) return
        prefs?.edit()?.putString("run", engine.serializeRun())?.apply()
        prefs?.edit()?.putLong("lastSeen", System.currentTimeMillis())?.apply()
    }

    private fun clearRun() { prefs?.edit()?.remove("run")?.apply() }

    private fun saveCodex() {
        val e = prefs?.edit() ?: return
        e.putString("codexSeen", codexSeen.joinToString(";"))
        e.putString("codexKills", codexKills.entries.joinToString(";") { "${it.key}=${it.value}" })
        e.putStringSet("codexMet", codexMet)
        e.putStringSet("codexBiomes", codexBiomes)
        e.putString("codexItems", codexItems.joinToString(";"))
        e.apply()
    }

    private fun brainValues(s: String): Map<String, Float> =
        s.split(";").mapNotNull { pair ->
            val kv = pair.split("=", limit = 2)
            val v = kv.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
            if (kv[0] in setOf("baseline", "hasBaseline", "lessons")) null else kv[0] to v
        }.toMap()

    // ================= evening report (WorkManager) =================

    private fun scheduleReport() {
        val ctx = appCtx ?: return
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 21)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis())
                add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val req = androidx.work.PeriodicWorkRequestBuilder<ReportWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(cal.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .build()
        try {
            androidx.work.WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "evening_report", androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (_: Exception) {}
    }

    // ================= export / import =================

    private val EXPORT_STR = listOf("brain", "lore", "genePool", "mdp", "personaScores",
        "hall", "lastSaga", "fallenHall", "curve", "distraction", "hourMs",
        "heroAvatar", "heroName", "codexSeen", "codexKills", "codexItems")
    private val EXPORT_SET = listOf("blocklist", "talents", "feats", "focusDays",
        "siteBlocklist", "codexMet", "codexBiomes")
    private val EXPORT_INT = listOf("embers", "heroCount", "bestDepth", "heroesFallen",
        "totalKills", "totalFloors", "lessons", "generation", "wardStart", "wardEnd")

    fun exportLineage(): String? {
        val p = prefs ?: return null
        return try {
            val o = org.json.JSONObject()
            EXPORT_STR.forEach { k -> p.getString(k, null)?.let { o.put(k, it) } }
            EXPORT_SET.forEach { k ->
                o.put(k, org.json.JSONArray(p.getStringSet(k, emptySet()).toList()))
            }
            EXPORT_INT.forEach { k -> o.put(k, p.getInt(k, 0)) }
            o.put("identityChosen", p.getBoolean("identityChosen", false))
            o.put("darkAdvances", p.getBoolean("darkAdvances", false))
            o.put("shield", p.getBoolean("shield", false))
            o.put("warded", p.getBoolean("warded", false))
            o.put("version", 1)
            o.toString()
        } catch (e: Exception) { null }
    }

    fun importLineage(json: String): Boolean {
        val p = prefs ?: return false
        return try {
            val o = org.json.JSONObject(json)
            val e = p.edit()
            EXPORT_STR.forEach { k -> if (o.has(k)) e.putString(k, o.getString(k)) }
            EXPORT_SET.forEach { k ->
                if (o.has(k)) {
                    val arr = o.getJSONArray(k)
                    e.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }
            EXPORT_INT.forEach { k -> if (o.has(k)) e.putInt(k, o.getInt(k)) }
            if (o.has("identityChosen")) e.putBoolean("identityChosen", o.getBoolean("identityChosen"))
            if (o.has("darkAdvances")) e.putBoolean("darkAdvances", o.getBoolean("darkAdvances"))
            if (o.has("shield")) e.putBoolean("shield", o.getBoolean("shield"))
            if (o.has("warded")) e.putBoolean("warded", o.getBoolean("warded"))
            e.commit()
        } catch (e: Exception) { false }
    }
}
