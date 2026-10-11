package com.focusrealm.everdeep

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.focusrealm.everdeep.game.Engine
import com.focusrealm.everdeep.game.FloorFeat
import com.focusrealm.everdeep.game.FloorItem
import com.focusrealm.everdeep.game.FeatKind
import com.focusrealm.everdeep.game.Rarity
import com.focusrealm.everdeep.game.Tile

class GameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private data class StoneLine(val text: String, val color: Int)

    private val tilePaint = Paint()
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val bannerPaint = Paint()
    private val overlayText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = 0xFFE9DFC8.toInt()
    }

    private val itemMap = HashMap<Int, FloorItem>()
    private val featMap = HashMap<Int, FloorFeat>()
    private val monsterMap = HashMap<Int, com.focusrealm.everdeep.game.Monster>()

    private var zoom = 1f
    private var panX = 0f; private var panY = 0f
    private val scaler = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoom = (zoom * d.scaleFactor).coerceIn(0.7f, 3f)
                invalidate()
                return true
            }
        })
    private val gestures = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                zoom = 1f; panX = 0f; panY = 0f; invalidate(); return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent,
                                  dx: Float, dy: Float): Boolean {
                if (zoom > 1.01f) {
                    panX = (panX - dx).coerceIn(-width * zoom / 2f, width * zoom / 2f)
                    panY = (panY - dy).coerceIn(-height * zoom / 2f, height * zoom / 2f)
                    invalidate(); return true
                }
                return false
            }
        })

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaler.onTouchEvent(ev)
        gestures.onTouchEvent(ev)
        return true
    }

    private fun dp(v: Int) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF080706.toInt())
        val e = GameHost.engineRef() ?: run {
            overlayText.textSize = dp(13)
            canvas.drawText("· generating the deep ·", width / 2f, height / 2f, overlayText)
            return
        }
        drawDungeon(canvas, e)
        when (GameHost.status.value.state) {
            GameHost.State.PAUSED -> drawPauseCard(canvas)
            GameHost.State.FALLEN -> drawTombstone(canvas)
            else -> {}
        }
        if (GameHost.status.value.state == GameHost.State.FALLEN) postInvalidateDelayed(650)
    }

    // ---------- dungeon ----------

    private fun drawDungeon(canvas: Canvas, e: Engine) {
        val cell = width / (26f / zoom)
        if (cell <= 0f) return
        val cols = (width / cell).toInt() + 1
        val rows = (height / cell).toInt() + 1
        val camX0 = if (e.W <= cols) -(cols - e.W) / 2f
                   else (e.hero.x - cols / 2).coerceIn(0, e.W - cols).toFloat()
        val camY0 = if (e.H <= rows) -(rows - e.H) / 2f
                   else (e.hero.y - rows / 2).coerceIn(0, e.H - rows).toFloat()
        val camX = camX0 - panX / cell
        val camY = camY0 - panY / cell
        val bx = camX.toInt(); val by = camY.toInt()

        itemMap.clear()
        for (itm in e.items) itemMap[itm.y * e.W + itm.x] = itm
        featMap.clear()
        for (f in e.feats) featMap[f.y * e.W + f.x] = f
        monsterMap.clear()
        for (m in e.monsters) if (m.alive) monsterMap[m.y * e.W + m.x] = m

        val decorColors = HashMap<Char, Int>()
        for (t in e.biome.decor) decorColors[t.first] = t.third
        for (t in e.biome.wallDecor) if (!decorColors.containsKey(t.first)) decorColors[t.first] = t.third
        for (c in e.biome.clusters) if (!decorColors.containsKey(c.glyph)) decorColors[c.glyph] = c.color
        decorColors['%'] = 0xFF6B5A4A.toInt()   // bones of the fallen

        for (gy in 0 until rows) {
            val my = by + gy
            if (my < 0 || my >= e.H) continue
            for (gx in 0 until cols) {
                val mx = bx + gx
                if (mx < 0 || mx >= e.W) continue
                if (!e.explored[my][mx]) continue
                val vis = e.visible[my][mx]
                val t = e.map[my][mx]

                tilePaint.color = when {
                    t == Tile.WALL -> if (vis) e.biome.wallLit else e.biome.wallDim
                    else           -> if (vis) e.biome.floorLit else e.biome.floorDim
                }
                canvas.drawRect(gx * cell + 1f, gy * cell + 1f,
                                (gx + 1) * cell - 1f, (gy + 1) * cell - 1f, tilePaint)

                val d = e.decor[my][mx]
                if (d != ' ') {
                    val dc = decorColors[d]
                    if (dc != null) {
                        glyphPaint.textSize = cell * 0.6f
                        glyphPaint.alpha = if (vis) 200 else 95
                        glyphPaint.color = dc
                        glyphPaint.isFakeBoldText = false
                        canvas.drawText(d.toString(), gx * cell + cell / 2f,
                                        gy * cell + cell * 0.72f, glyphPaint)
                    }
                }

                var glyph: Char? = null
                var color = 0
                var isElite = false
                var itemBold = false
                var rivalBold = false
                if (t == Tile.STAIRS) { glyph = '>'; color = e.biome.accent }

                val k = my * e.W + mx
                val feat = featMap[k]
                if (feat != null && feat.revealed && !feat.used) {
                    glyph = feat.glyph
                    color = when (feat.kind) {
                        FeatKind.CHEST  -> 0xFFC9A96A.toInt()
                        FeatKind.LOCKED -> 0xFF8D8168.toInt()
                        FeatKind.SHRINE -> 0xFFE9DFC8.toInt()
                        FeatKind.SPIKES -> 0xFFC4483C.toInt()
                        FeatKind.GAS    -> 0xFFB39DDB.toInt()
                        FeatKind.GLYPH  -> 0xFF7FB4C9.toInt()
                    }
                }
                val itm = itemMap[k]
                if (itm != null && (vis || e.knownItems.contains(k))) {
                    glyph = itm.glyph
                    color = itm.item.rarity.color
                    itemBold = itm.item.rarity >= Rarity.EPIC
                }
                val m = if (vis) monsterMap[k] else null
                if (m != null) {
                    glyph = m.type.glyph
                    isElite = m.elite
                    color = if (m.elite) 0xFFE0B84F.toInt() else m.type.color
                }
                val n = if (vis) e.npcAt(mx, my) else null
                if (n != null) { glyph = n.kind.glyph; color = n.kind.color }
                val rv = if (vis) e.rivalAt(mx, my) else null
                if (rv != null) { glyph = rv.glyph; color = 0xFFB8C4C4.toInt(); rivalBold = true }
                val fam = e.familiar
                if (fam != null && vis && e.heroAlive && mx == fam.x && my == fam.y) {
                    glyph = fam.glyph; color = fam.color
                }
                var avatarStr: String? = null
                if (mx == e.hero.x && my == e.hero.y) {
                    if (e.heroAlive) avatarStr = GameHost.heroAvatar
                    else { glyph = '†'; color = 0xFFC4483C.toInt() }
                }
                if (avatarStr != null) {
                    glyphPaint.textSize = if (avatarStr!!.length > 1) cell * 1.05f else cell * 0.85f
                    glyphPaint.alpha = 255
                    glyphPaint.color = 0xFFF0A35C.toInt()
                    glyphPaint.isFakeBoldText = false
                    canvas.drawText(avatarStr, gx * cell + cell / 2f,
                                    gy * cell + cell * 0.74f, glyphPaint)
                } else if (glyph != null) {
                    glyphPaint.textSize = cell * 0.8f
                    glyphPaint.alpha = if (vis) 255 else 125
                    glyphPaint.color = color
                    glyphPaint.isFakeBoldText = isElite || itemBold || rivalBold
                    canvas.drawText(glyph.toString(), gx * cell + cell / 2f,
                                    gy * cell + cell * 0.74f, glyphPaint)
                    glyphPaint.isFakeBoldText = false
                }
            }
        }
    }

    // ---------- pause card ----------

    private fun drawPauseCard(canvas: Canvas) {
        val st = GameHost.status.value
        val l1 = "⏸  THE HERO WAITS"
        val l2 = "${st.appLabel.uppercase()} — RIVALS DELVE ON"

        overlayText.textSize = dp(12)
        val w = maxOf(overlayText.measureText(l1), overlayText.measureText(l2)) + dp(30)
        val lineH = dp(12) * 1.5f
        val h = lineH * 2 + dp(20)
        val left = width / 2f - w / 2f
        val top = height / 2f - h / 2f

        bannerPaint.color = 0xB3762A22.toInt()
        canvas.drawRoundRect(left, top, left + w, top + h, dp(6), dp(6), bannerPaint)

        val cy = top + dp(15)
        overlayText.color = 0xFFE9DFC8.toInt()
        overlayText.isFakeBoldText = true
        canvas.drawText(l1, width / 2f, cy, overlayText)
        overlayText.isFakeBoldText = false
        overlayText.color = 0xFFD9B8AC.toInt()
        canvas.drawText(l2, width / 2f, cy + lineH, overlayText)
    }

    // ---------- tombstone ----------

    private fun drawTombstone(canvas: Canvas) {
        val f = GameHost.fallenRun.value ?: return

        val ART = 0xFF5A5040.toInt()
        val BONE = 0xFFE9DFC8.toInt()
        val EMBER = 0xFFD98E4A.toInt()
        val DIMC = 0xFF8D8168.toInt()
        val DANGER = 0xFFC4483C.toInt()
        val BRIGHT = 0xFFF0A35C.toInt()

        val ts = dp(11)
        overlayText.textSize = ts
        val inner = 18
        fun body(content: String, color: Int) =
            StoneLine("   |" + centerIn(content, inner) + "|", color)

        val nameLines = wrapName(f.name, inner)
        val rows = ArrayList<StoneLine>()
        rows.add(StoneLine("     ________________", ART))
        rows.add(StoneLine("    /                \\", ART))
        rows.add(body(f.avatar, BONE))
        rows.add(body("R.I.P.", BONE))
        rows.add(body("", ART))
        nameLines.forEach { rows.add(body(it, EMBER)) }
        if (nameLines.size == 1) rows.add(body("", ART))
        if (f.persona.isNotEmpty()) rows.add(body(f.persona, DIMC))
        rows.add(body("lv ${f.level}", DIMC))
        rows.add(body("floor ${f.depth}", DIMC))
        rows.add(body("${f.turns} turns", DIMC))
        rows.add(body("", ART))
        rows.add(StoneLine(" __|" + "_".repeat(inner) + "|__", ART))

        val epi1 = if (f.epitaph.isNotEmpty()) "💀 ${f.epitaph}"
                   else "💀 slain by the ${f.slainBy}"
        val epi2 = "⚔️ ${f.kills} slain · ${f.gold} gold · by the ${f.slainBy}"
        val epi3 = "🕯️ a successor takes the torch…"

        val chW = overlayText.measureText("M")
        val lineH = ts * 1.25f
        val stoneW = rows.maxOf { it.text.length } * chW
        val blockW = maxOf(stoneW, overlayText.measureText(epi1),
                           overlayText.measureText(epi2), overlayText.measureText(epi3)) + dp(30)
        val blockH = (rows.size + 3) * lineH + dp(22)
        val left = width / 2f - blockW / 2f
        val top = height / 2f - blockH / 2f

        bannerPaint.color = 0xE0080706.toInt()
        canvas.drawRoundRect(left, top, left + blockW, top + blockH, dp(8), dp(8), bannerPaint)
        bannerPaint.style = Paint.Style.STROKE
        bannerPaint.strokeWidth = dp(1)
        bannerPaint.color = 0xFF3A322A.toInt()
        canvas.drawRoundRect(left, top, left + blockW, top + blockH, dp(8), dp(8), bannerPaint)
        bannerPaint.style = Paint.Style.FILL

        overlayText.textAlign = Paint.Align.LEFT
        var y = top + dp(16) + ts
        for (r in rows) {
            overlayText.color = r.color
            canvas.drawText(r.text, width / 2f - stoneW / 2f, y, overlayText)
            y += lineH
        }

        overlayText.textAlign = Paint.Align.CENTER
        overlayText.color = DANGER
        canvas.drawText(epi1, width / 2f, y, overlayText); y += lineH
        overlayText.color = DIMC
        canvas.drawText(epi2, width / 2f, y, overlayText); y += lineH
        overlayText.color = BRIGHT
        overlayText.alpha = if ((System.currentTimeMillis() / 650) % 2 == 0L) 255 else 80
        canvas.drawText(epi3, width / 2f, y, overlayText)
        overlayText.alpha = 255
    }

    private fun centerIn(s: String, w: Int): String {
        val pad = w - s.length
        if (pad <= 0) return s
        val l = pad / 2
        return " ".repeat(l) + s + " ".repeat(pad - l)
    }

    private fun wrapName(name: String, w: Int): List<String> {
        if (name.length <= w) return listOf(name)
        val mid = name.length / 2
        var best = -1; var bestDist = Int.MAX_VALUE
        for (i in name.indices) if (name[i] == ' ') {
            val d = Math.abs(i - mid)
            if (d < bestDist) { bestDist = d; best = i }
        }
        if (best < 0) return listOf(name)
        return listOf(name.substring(0, best).trim(), name.substring(best + 1).trim())
    }
}
