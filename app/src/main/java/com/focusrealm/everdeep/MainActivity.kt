package com.focusrealm.everdeep

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.focusrealm.everdeep.game.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val heroNameV by lazy { findViewById<TextView>(R.id.heroName) }
    private val recordsV by lazy { findViewById<TextView>(R.id.recordsLine) }
    private val standingsV by lazy { findViewById<TextView>(R.id.standings) }
    private val depthV by lazy { findViewById<TextView>(R.id.depthNum) }
    private val turnV by lazy { findViewById<TextView>(R.id.turnLabel) }
    private val statsV by lazy { findViewById<TextView>(R.id.heroStats) }
    private val hpAsciiV by lazy { findViewById<TextView>(R.id.hpAscii) }
    private val xpAsciiV by lazy { findViewById<TextView>(R.id.xpAscii) }
    private val skillsV by lazy { findViewById<TextView>(R.id.skillsRow) }
    private val portraitV by lazy { findViewById<TextView>(R.id.portrait) }
    private val dotV by lazy { findViewById<View>(R.id.statusDot) }
    private val statusV by lazy { findViewById<TextView>(R.id.statusText) }
    private val timerV by lazy { findViewById<TextView>(R.id.timer) }
    private val gameV by lazy { findViewById<GameView>(R.id.game) }
    private val logList by lazy { findViewById<LinearLayout>(R.id.logList) }
    private val logScroll by lazy { findViewById<ScrollView>(R.id.logScroll) }
    private val sessionBtn by lazy { findViewById<TextView>(R.id.sessionBtn) }
    private val speedBtn by lazy { findViewById<TextView>(R.id.speedBtn) }
    private val blockRows by lazy { findViewById<LinearLayout>(R.id.blockRows) }
    private val pkgInput by lazy { findViewById<EditText>(R.id.pkgInput) }
    private val bannerV by lazy { findViewById<TextView>(R.id.banner) }

    private var shieldBtn: TextView? = null
    private var soundBtn: TextView? = null
    private var darkBtn: TextView? = null
    private var vpnChip: TextView? = null
    private var wardChip: TextView? = null

    private var lastHero: GameHost.HeroUi? = null
    private var lastState: GameHost.State? = null
    private var lastUiState: GameHost.State? = null
    private var blink = false
    private var lastSessSec = 0; private var lastGoalMin = 0; private var lastSessElapsed = 0
    private var creatorShown = false
    private var logPool: Array<TextView>? = null
    private var lastBlockKey = "\u0000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusBus.init(this)
        GameHost.init(this)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        sessionBtn.setOnClickListener {
            if (GameHost.running.value) GameHost.stopSession() else GameHost.startSession()
        }
        speedBtn.setOnClickListener {
            GameHost.setSpeed(when (GameHost.speed.value) { 1 -> 2; 2 -> 4; 4 -> 8; else -> 1 })
        }
        findViewById<TextView>(R.id.blockBtn).setOnClickListener {
            val p = findViewById<View>(R.id.blockPanel)
            p.visibility = if (p.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.heroBtn).setOnClickListener { showCreator() }
        findViewById<TextView>(R.id.chronBtn).setOnClickListener { ChronicleDialog(this).show() }
        findViewById<TextView>(R.id.legacyBtn).setOnClickListener { LegacyDialog(this).show() }
        findViewById<TextView>(R.id.codexBtn).setOnClickListener { CodexDialog(this).show() }
        findViewById<TextView>(R.id.addBtn).setOnClickListener { addPackage() }
        findViewById<TextView>(R.id.previewBtn).setOnClickListener { FocusBus.previewPause() }
        findViewById<TextView>(R.id.a11yBtn).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        bannerV.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        buildSessionRow()
        buildVpnRow()
        buildWardRow()
        handleShare(intent)

        GameHost.startSession()
        observe()
        creatorShown = savedInstanceState?.getBoolean("creator", false) ?: false
        if (!GameHost.identityChosen && !creatorShown) showCreator()
    }

    override fun onResume() {
        super.onResume()
        GameHost.activityVisible = true
        bannerV.visibility = if (monitorEnabled()) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        GameHost.activityVisible = false
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        handleShare(i)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putBoolean("creator", creatorShown)
    }

    // ---------- creator ----------

    private fun showCreator() {
        creatorShown = true
        CreatorDialog(this) { name, avatar ->
            GameHost.setIdentity(name, avatar)
            GameHost.restartRun()
        }.show()
    }

    // ---------- share-to-block ----------

    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val txt = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val pkg = txt.trim().removePrefix("package:").lowercase()
        if (Regex("^[a-z][\\w]*(\\.[\\w]+)+$").matches(pkg)) {
            FocusBus.add(pkg)
            Toast.makeText(this, "⛓️ ${FocusBus.labelFor(pkg)} chained to the blocklist",
                Toast.LENGTH_LONG).show()
        }
    }

    // ---------- observe ----------

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    GameHost.heroUi.collect { h ->
                        lastHero = h
                        heroNameV.text = "${h.avatar} №${h.lineage} · ${h.name} — ${h.persona}"
                        depthV.text = h.depth.toString()
                        turnV.text = "${h.biomeIcon} ${h.biomeName} · turn ${h.turns}"
                        statsV.text = "lv ${h.level} · ⚔️ ${h.atk} · 🛡️ ${h.def}" +
                                      " · 🧪 ×${h.potions} · 💰 ${h.gold} · ☠️ ${h.kills}"
                        hpAsciiV.text = "HP ${bar(h.hp, h.maxHp)} ${h.hp}/${h.maxHp}"
                        hpAsciiV.setTextColor(getColor(
                            if (h.hp * 100 <= h.maxHp * 35) R.color.danger else R.color.ember))
                        xpAsciiV.text = "XP ${bar(h.xp, h.xpNext)} ${h.xp}/${h.xpNext}"
                        renderPortrait()
                        gameV.invalidate()
                    }
                }
                launch {
                    GameHost.status.collect {
                        lastState = it.state
                        renderStatus(it)
                    }
                }
                launch { GameHost.log.collect { renderLog(it) } }
                launch {
                    GameHost.recordsFlow.collect { r ->
                        recordsV.text = "🏆 best floor ${r.bestDepth}" +
                                        " · ⚰️ ${r.heroesFallen} fallen" +
                                        " · ⚔️ ${r.totalKills} slain" +
                                        " · 🧠 ${r.lessons} lessons" +
                                        " · 🧬 g${r.generation}"
                    }
                }
                launch {
                    GameHost.standings.collect { s ->
                        standingsV.visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
                        standingsV.text = s
                        standingsV.setTextColor(getColor(
                            if (s.contains("below you")) R.color.danger else R.color.moss))
                    }
                }
                launch { GameHost.skillsUi.collect { renderSkills(it) } }
                launch { GameHost.speed.collect { speedBtn.text = "${it}×" } }
                launch {
                    GameHost.running.collect { run ->
                        sessionBtn.text = if (run) "⏸ PAUSE" else "▶ RESUME"
                    }
                }
                launch {
                    GameHost.sessionSeconds.collect { s -> lastSessSec = s; renderTimer() }
                }
                launch {
                    GameHost.sessionGoal.collect { g -> lastGoalMin = g; renderTimer(); restyleChips() }
                }
                launch {
                    GameHost.sessionElapsed.collect { e -> lastSessElapsed = e; renderTimer() }
                }
                launch { GameHost.auraTier.collect { renderTimer() } }
                launch { FocusBus.blocklist.collect { rebuildBlocklist() } }
                launch {
                    while (isActive) {
                        delay(900)
                        if (lastState == GameHost.State.FOCUSED || lastState == GameHost.State.IDLE) {
                            blink = !blink
                            renderPortrait()
                        }
                    }
                }
            }
        }
    }

    // ---------- portrait & bars ----------

    private fun renderPortrait() {
        if (GameHost.heroAvatar.length > 1) {
            portraitV.textSize = 26f
            portraitV.text = GameHost.heroAvatar
            portraitV.setTextColor(getColor(
                if (lastState == GameHost.State.FALLEN || lastState == GameHost.State.PAUSED)
                    R.color.danger else R.color.bone))
            return
        }
        portraitV.textSize = 13f
        val h = lastHero
        val st = lastState
        var face = "(o_o)\n/|_|\\"
        var color = getColor(R.color.bone)
        when {
            st == GameHost.State.FALLEN -> {
                face = "(×_×)\n/|_|\\"; color = getColor(R.color.danger)
            }
            st == GameHost.State.PAUSED -> {
                face = "(?.?)\n/|_|\\"; color = getColor(R.color.danger)
            }
            h != null && h.hp * 100 <= h.maxHp * 35 -> {
                face = "(x_x)\n/|;|\\"; color = getColor(R.color.danger)
            }
            h != null && h.hp * 100 <= h.maxHp * 70 -> {
                face = (if (blink) "(o_-)" else "(-_-)") + "\n/|~|\\"
            }
            h != null -> {
                face = (if (blink) "(—_—)" else "(o_o)") + "\n/|_|\\"
            }
        }
        portraitV.text = face
        portraitV.setTextColor(color)
    }

    private fun bar(cur: Int, max: Int): String {
        val w = 10
        val fill = if (max <= 0) 0 else ((cur.toDouble() / max) * w).toInt().coerceIn(0, w)
        return "▓".repeat(fill) + "░".repeat(w - fill)
    }

    private fun renderSkills(list: List<GameHost.SkillUi>) {
        if (list.isEmpty()) { skillsV.text = ""; return }
        val act = list.filter { it.active }
        val pas = list.count { !it.active }
        val sb = StringBuilder()
        for ((i, s) in act.withIndex()) {
            if (i > 0) sb.append("  ")
            sb.append(s.emoji).append(' ').append(s.name)
            if (s.cd > 0) sb.append("⏳").append(s.cd)
        }
        if (pas > 0) {
            if (act.isNotEmpty()) sb.append("  ")
            sb.append("✦ +").append(pas).append(" arts")
        }
        skillsV.text = sb.toString()
    }

    // ---------- status ----------

    private fun renderStatus(s: GameHost.StatusUi) {
        dotV.clearAnimation()
        if (lastUiState != s.state) {
            when (s.state) {
                GameHost.State.PAUSED -> vibrate(longArrayOf(0, 180))
                GameHost.State.FALLEN -> vibrate(longArrayOf(0, 120, 80, 120, 80, 260))
                else -> {}
            }
            lastUiState = s.state
        }
        when (s.state) {
            GameHost.State.FOCUSED -> {
                dotV.setBackgroundResource(R.drawable.dot_moss)
                val noMonitor = !FocusBus.monitorActive && GameHost.running.value
                statusV.setTextColor(getColor(if (noMonitor) R.color.danger else R.color.dim))
                statusV.text = (if (noMonitor) "⚠️ NO MONITOR — distractions unwatched · "
                                else "🟢 FOCUSED · ") +
                    (FocusBus.currentApp.value?.let {
                        if (it == packageName) "Everdeep" else FocusBus.labelFor(it)
                    } ?: "your device")
            }
            GameHost.State.PAUSED -> {
                dotV.setBackgroundResource(R.drawable.dot_danger)
                statusV.setTextColor(getColor(R.color.danger))
                statusV.text = "🚫 DISTRACTED · ${s.appLabel}"
                dotV.startAnimation(AlphaAnimation(1f, 0.15f).apply {
                    duration = 450; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
                })
            }
            GameHost.State.FALLEN -> {
                dotV.setBackgroundResource(R.drawable.dot_danger)
                statusV.setTextColor(getColor(R.color.danger))
                statusV.text = "⚰️ THE HERO HAS FALLEN"
            }
            GameHost.State.IDLE -> {
                dotV.setBackgroundResource(R.drawable.dot_dim)
                statusV.setTextColor(getColor(R.color.dim))
                statusV.text = "⏸ PAUSED"
            }
        }
        gameV.invalidate()
    }

    private fun vibrate(pattern: LongArray) {
        val v = getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // ---------- timer ----------

    private fun renderTimer() {
        timerV.text = if (lastGoalMin > 0)
            "⏳ %02d:%02d · %02d:%02d".format(lastSessSec / 60, lastSessSec % 60,
                                             lastSessElapsed / 60, lastSessElapsed % 60)
        else "⏳ %02d:%02d".format(lastSessSec / 60, lastSessSec % 60)
        val aura = "🔥".repeat(GameHost.auraTier.value)
        if (aura.isNotEmpty()) timerV.text = "${timerV.text} $aura"
    }

    // ---------- log ----------

    private fun renderLog(lines: List<GameHost.LogLine>) {
        if (logPool == null) {
            logPool = Array(6) {
                TextView(this).apply {
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setPadding(0, dp(3), 0, dp(3))
                }
            }
            logPool!!.forEach { logList.addView(it) }
        }
        val last6 = lines.takeLast(6)
        for (i in 0 until 6) {
            val tv = logPool!![i]
            if (i >= last6.size) { tv.visibility = View.GONE; continue }
            tv.visibility = View.VISIBLE
            val l = last6[i]
            tv.text = "${emojiFor(l.text, l.tone)}  T${l.turn} · ${l.text}" +
                      (if (l.n > 1) " ×${l.n}" else "")
            tv.setTextColor(getColor(when (l.tone) {
                Tone.GOOD -> R.color.moss
                Tone.BAD -> R.color.danger
                Tone.EPIC -> R.color.emberBright
                else -> R.color.dim
            }))
        }
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun emojiFor(text: String, tone: Tone): String {
        val t = text.lowercase()
        return when {
            "days below" in t -> "🧝"
            "enters the everdeep" in t -> "🚪"
            "chapter" in t -> "📖"
            "life:" in t || "born again as" in t || "walks these stairs" in t
                    || "way: forward" in t || "logic —" in t -> "🎭"
            "lineage" in t -> "🧠"
            "champion's blood" in t || "bred from" in t || "stranger's blood" in t
                    || "line scores" in t || "ledger of blood" in t -> "🧬"
            "learned ledger" in t || "learned judgments" in t -> "🧮"
            "the math says no" in t || "weighs it" in t || "cornered" in t -> "🤔"
            "tactical maps" in t || "reads the room" in t || "quieter path" in t -> "🌳"
            "narrows the world" in t || "every plan gets shorter" in t -> "🩸"
            "streak talks" in t || "burns hotter" in t -> "🔥"
            "bars the way" in t || "before the torch was lit" in t -> "👑"
            "is felled" in t || "goes down swinging" in t -> "👑"
            "enraged" in t || "fights harder" in t -> "💢"
            "venom" in t -> "🐍"
            "palms" in t || "light fingers" in t -> "🤏"
            "refuses the first death" in t || "disagrees" in t -> "😤"
            "turns tail" in t || "breaks and runs" in t -> "💨"
            "calls —" in t || "its kin stir" in t -> "🐺"
            "scroll" in t -> "📜"
            "have we met" in t -> "🤔"
            "walks the halls now" in t || "keeper hunts" in t -> "👁️"
            "keeper waits below" in t || "already angry" in t -> "⚠️"
            "wields the" in t -> "🗡️"
            "fastens on" in t -> "🛡️"
            "hangs at the neck" in t -> "📿"
            "pockets the" in t || "tucks the" in t -> "🎒"
            "salvages" in t -> "⚙️"
            "hums once" in t || "presses itself" in t -> "✨"
            "clatters to the floor" in t -> "🎁"
            "opened a chest" in t || "forced a chest" in t -> "🎁"
            "shrine" in t -> "⛩️"
            "spikes" in t || "hair-thin wire" in t -> "⚠️"
            "burns the venom" in t -> "🧪"
            "wanted:" in t || "posts a bounty" in t || "bounty" in t -> "🎯"
            "exit is a kill" in t || "longer than through" in t -> "🗡️"
            "first blood" in t -> "🩸"
            "souls behind the torch" in t -> "☠️"
            "falls" in t || "breaks apart" in t || "clean stroke" in t || "bright moment" in t
                    || "last lesson" in t || "exhale" in t || "unkept" in t -> "⚔️"
            "lands a blow" in t || "claws find" in t || "slammed door" in t -> "🩸"
            "returning spite" in t -> "🌵"
            "steel answers" in t -> "⚔️"
            "words of ash" in t -> "🔥"
            "the water remembers" in t -> "🌊"
            "green things lean" in t -> "🌿"
            "something vast listens" in t -> "🌑"
            "art of" in t || "grows into a new art" in t || "secret —" in t
                    || "is taught by" in t || "swaps out" in t -> "📜"
            "freezes — 🌐" in t -> "🌐"
            "presses on" in t -> "🔥"
            "descends" in t -> "🕳️"
            "reaches level" in t -> "🌟"
            "elixir" in t -> "✨"
            "potion" in t -> "🧪"
            "slain by" in t || "lands the last blow" in t -> "💀"
            "run ends" in t -> "⚰️"
            "slays" in t || "crits" in t || "strikes" in t -> "⚔️"
            "gold" in t -> "💰"
            "sells" in t -> "🪙"
            "leans close" in t -> "🗣️"
            "stairs paid your tab" in t -> "🎁"
            "dice" in t -> "🎲"
            "ghost" in t || "whispers" in t -> "👻"
            "sings" in t -> "🎶"
            "hermit" in t -> "🧙"
            "forge" in t || "grind" in t || "plate" in t -> "⚒️"
            "chart" in t -> "🗺️"
            "is delving this floor too" in t || "going deeper" in t
                    || "shadows the same halls" in t -> "🏁"
            "cuts down" in t -> "⚔️"
            "draws ahead" in t -> "🥇"
            "takes the stairs down" in t || "drifts up the stairwell" in t -> "🕳️"
            "steel meets steel" in t || "fights like the floor" in t -> "⚔️"
            "takes the deep's prize" in t -> "💀"
            "a campfire burns" in t || "the fire accepts" in t -> "🔥"
            "gold changes hands" in t || "the coin wins" in t -> "🤝"
            "thick dark" in t -> "🌙"
            "blood tide" in t -> "🩸"
            "the hush" in t -> "🤫"
            "gilded halls" in t -> "💰"
            "feat earned" in t -> "🏅"
            "adopts her" in t || "falls in step" in t || "snaps at" in t
                    || "earns its keep" in t || "befriended" in t -> "🐾"
            "roots out" in t -> "💰"
            "hangs back" in t -> "🙈"
            "the dark advances" in t || "pact of stillness" in t -> "⚠️"
            "purses fill" in t || "rivals feast" in t -> "💰"
            "kindled" in t || "burning —" in t || "everflame" in t || "transcendent" in t -> "✨"
            "aura gutters" in t || "flame dips" in t -> "🕯️"
            "did not sleep" in t -> "💨"
            "session begins" in t || "focus aura" in t -> "✨"
            "session complete" in t -> "🎯"
            "vow kept" in t -> "🕯️"
            "torch relights" in t -> "🕯️"
            "warded hours" in t -> "⏰"
            "saga" in t -> "📜"
            "the run, in numbers" in t -> "📊"
            tone == Tone.GOOD -> "✦"
            tone == Tone.EPIC -> "✧"
            tone == Tone.BAD -> "⚠️"
            else -> "·"
        }
    }

    // ---------- blocklist ----------

    private fun rebuildBlocklist() {
        val key = FocusBus.blocklist.value.sorted().joinToString(",")
        if (key == lastBlockKey) return
        lastBlockKey = key
        blockRows.removeAllViews()
        val pm = packageManager
        val pkgs = LinkedHashSet<String>()
        FocusBus.CATALOG.forEach { pkgs.add(it.pkg) }
        FocusBus.blocklist.value.forEach { pkgs.add(it) }
        for (pkg in pkgs.sortedBy { FocusBus.labelFor(it).lowercase() }) {
            val installed = try { pm.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
            val cb = CheckBox(this)
            cb.text = FocusBus.labelFor(pkg) + (if (installed) "" else "  (not installed)")
            cb.textSize = 13f
            cb.alpha = if (installed) 1f else 0.45f
            cb.setTextColor(getColor(R.color.bone))
            cb.buttonTintList = ColorStateList.valueOf(getColor(R.color.ember))
            cb.isChecked = FocusBus.blocklist.value.contains(pkg)
            cb.setOnCheckedChangeListener { _, _ -> FocusBus.toggle(pkg) }
            blockRows.addView(cb)
        }
    }

    private fun addPackage() {
        val pkg = pkgInput.text.toString().trim()
        if (!pkg.contains(".")) {
            Toast.makeText(this, "Use a full package id, e.g. com.company.app", Toast.LENGTH_SHORT).show()
            return
        }
        FocusBus.add(pkg)
        pkgInput.setText("")
    }

    // ---------- chips: sessions / shield / sound / dark ----------

    private fun chipView(text: String, active: Boolean, weight: Float,
                         onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text; textSize = 11f; typeface = Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(if (active) R.color.bg else R.color.bone))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(getColor(if (active) R.color.ember else R.color.surface))
                setStroke((1 * d).toInt(), getColor(R.color.line)); cornerRadius = 4 * d
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, (32 * d).toInt(), weight)
                .apply { marginEnd = (6 * d).toInt() }
        }
    }

    private fun buildSessionRow() {
        val row = findViewById<LinearLayout>(R.id.sessionRow)
        val goals = listOf(15, 25, 45, 60)
        goals.forEachIndexed { i, m ->
            row.addView(chipView("${m}m", lastGoalMin == m, 1f) {
                GameHost.setGoal(m); restyleChips()
            })
        }
        row.addView(chipView("✕", lastGoalMin == 0, 0.6f) { GameHost.setGoal(0); restyleChips() })

        val d = resources.displayMetrics.density
        fun toggle(id: Int): TextView = TextView(this).apply {
            this.id = id; textSize = 11f; typeface = Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, (8 * d).toInt(), 0, 0)
        }
        shieldBtn = toggle(R.id.shieldBtn)
        soundBtn = toggle(R.id.soundBtn)
        darkBtn = toggle(R.id.darkBtn)
        val tr = findViewById<LinearLayout>(R.id.toggleRow)
        tr.addView(shieldBtn, LinearLayout.LayoutParams(0, -2, 1f))
        tr.addView(soundBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (6 * d).toInt() })
        tr.addView(darkBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (6 * d).toInt() })

        shieldBtn!!.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow “Display over other apps” to arm the shield",
                    Toast.LENGTH_LONG).show()
            } else { FocusBus.setShield(!FocusBus.shield); restyleToggles() }
        }
        soundBtn!!.setOnClickListener { FocusBus.setSound(!FocusBus.sound); restyleToggles() }
        darkBtn!!.setOnClickListener { FocusBus.setDark(!FocusBus.darkAdvances); restyleToggles() }
        restyleToggles(); restyleChips()
    }

    private fun restyleChips() {
        val row = findViewById<LinearLayout>(R.id.sessionRow)
        val goals = listOf(15, 25, 45, 60)
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i) as TextView
            val active = if (i < goals.size) lastGoalMin == goals[i] else lastGoalMin == 0
            v.setTextColor(getColor(if (active) R.color.bg else R.color.bone))
            (v.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                getColor(if (active) R.color.ember else R.color.surface))
        }
    }

    private fun restyleToggles() {
        shieldBtn?.text = if (FocusBus.shield) "🛡 SHIELD ON" else "🛡 SHIELD OFF"
        shieldBtn?.setTextColor(getColor(if (FocusBus.shield) R.color.moss else R.color.bone))
        soundBtn?.text = if (FocusBus.sound) "🔊 SOUND ON" else "🔇 SOUND OFF"
        soundBtn?.setTextColor(getColor(if (FocusBus.sound) R.color.moss else R.color.bone))
        darkBtn?.text = if (FocusBus.darkAdvances) "⚠️ DARK ADVANCES" else "🌙 SAFE PAUSE"
        darkBtn?.setTextColor(getColor(if (FocusBus.darkAdvances) R.color.danger else R.color.bone))
    }

    // ---------- site filter (VPN) ----------

    @Suppress("DEPRECATION")
    private fun buildVpnRow() {
        val row = findViewById<LinearLayout>(R.id.vpnRow)
        vpnChip = chipView("🌐 SITES OFF", false, 1.2f) { toggleVpn() }
        row.addView(vpnChip)
        row.addView(chipView("+ ADD SITE", false, 1f) {
            val d = findViewById<EditText>(R.id.siteInput).text.toString().trim().lowercase()
                .removePrefix("https://").removePrefix("www.").substringBefore("/")
            if (d.contains(".")) {
                FocusBus.addSite(d)
                findViewById<EditText>(R.id.siteInput).setText("")
                renderSites()
            } else Toast.makeText(this, "example.com", Toast.LENGTH_SHORT).show()
        })
        renderSites(); restyleVpn()
    }

    @Suppress("DEPRECATION")
    private fun toggleVpn() {
        if (FocusBus.vpnActive) {
            startService(Intent(this, FocusVpn::class.java).setAction(FocusVpn.ACTION_STOP))
            FocusBus.vpnActive = false
        } else {
            val consent = VpnService.prepare(this)
            if (consent != null) startActivityForResult(consent, 7)
            else startVpnNow()
        }
        restyleVpn()
    }

    private fun startVpnNow() {
        startService(Intent(this, FocusVpn::class.java))
        FocusBus.vpnActive = true
        restyleVpn()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, code: Int, data: Intent?) {
        super.onActivityResult(req, code, data)
        if (req == 7 && code == RESULT_OK) startVpnNow()
    }

    private fun restyleVpn() {
        vpnChip?.text = if (FocusBus.vpnActive) "🌐 SITES ON" else "🌐 SITES OFF"
        vpnChip?.setTextColor(getColor(if (FocusBus.vpnActive) R.color.moss else R.color.bone))
    }

    private fun renderSites() {
        val s = FocusBus.siteBlocklist.value
        findViewById<TextView>(R.id.sitesLine).text =
            if (s.isEmpty()) "(no sites blocked)" else "blocked: " + s.sorted().joinToString(", ")
    }

    // ---------- warded hours ----------

    private fun buildWardRow() {
        val row = findViewById<LinearLayout>(R.id.wardRow)
        findViewById<EditText>(R.id.wardStartIn).setText(FocusBus.wardStart.toString())
        findViewById<EditText>(R.id.wardEndIn).setText(FocusBus.wardEnd.toString())
        wardChip = chipView(if (FocusBus.warded) "⏰ WARDED ON" else "⏰ WARDED OFF",
            FocusBus.warded, 1f) {
            val s = findViewById<EditText>(R.id.wardStartIn).text.toString().toIntOrNull() ?: 9
            val e = findViewById<EditText>(R.id.wardEndIn).text.toString().toIntOrNull() ?: 17
            FocusBus.setWard(!FocusBus.warded, s, e)
            wardChip?.text = if (FocusBus.warded) "⏰ WARDED ON" else "⏰ WARDED OFF"
            wardChip?.setTextColor(getColor(if (FocusBus.warded) R.color.moss else R.color.bone))
            Toast.makeText(this,
                if (FocusBus.warded) "shield auto-arms weekdays $s–$e"
                else "warded hours off", Toast.LENGTH_SHORT).show()
        }
        row.addView(wardChip)
    }

    // ---------- misc ----------

    private fun monitorEnabled(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cls = FocusWatchService::class.java.name
        return s.split(':').any {
            it.equals("$packageName/$cls", true) ||
            it.equals("$packageName/.FocusWatchService", true)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
