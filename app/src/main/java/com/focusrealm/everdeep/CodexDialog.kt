package com.focusrealm.everdeep

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusrealm.everdeep.game.Bestiary
import com.focusrealm.everdeep.game.MType

/** The lineage's codex: every species seen, slain, and learned-to-fear. */
class CodexDialog(context: Context) : Dialog(context) {

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    override fun show() {
        super.show()
        window?.setBackgroundDrawable(ColorDrawable(0xFF0B0908.toInt()))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.84f).toInt())
    }

    private fun head(text: String): TextView = TextView(context).apply {
        this.text = text; textSize = 10f; typeface = Typeface.MONOSPACE
        letterSpacing = 0.15f; setTextColor(0xFF6B6150.toInt())
        setPadding(0, dp(14), 0, dp(4))
    }

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val seen = Bestiary.ALL.count { GameHost.codexIsSeen(it.id) }
        val slain = Bestiary.ALL.sumOf { GameHost.codexKillCount(it.id) }
        root.addView(TextView(context).apply {
            text = "📖 THE CODEX — $seen/100 species known · $slain slain"
            textSize = 13f; typeface = Typeface.MONOSPACE; letterSpacing = 0.1f
            setTextColor(0xFFD98E4A.toInt())
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(head("— THE BESTIARY — fear is measured in scar tissue —"))

        val books = listOf(
            "⚰️ The Ashen Crypts" to Bestiary.CRYPT,
            "🍄 The Fungal Hollows" to Bestiary.FUNGAL,
            "⚒️ The Ember Mines" to Bestiary.EMBER,
            "🌊 The Drowned Halls" to Bestiary.DROWNED,
            "🌑 The Voidreach" to Bestiary.VOIDREACH)
        for ((title, pool) in books) {
            body.addView(TextView(context).apply {
                text = title; textSize = 11f; typeface = Typeface.MONOSPACE
                setTextColor(0xFF8D8168.toInt()); setPadding(0, dp(6), 0, dp(2))
            })
            for (m in pool) renderMonster(body, m)
        }

        body.addView(head("— REALMS VISITED —"))
        body.addView(TextView(context).apply {
            val all = listOf("crypt" to "⚰️", "fungal" to "🍄", "ember" to "⚒️",
                             "drowned" to "🌊", "void" to "🌑")
            text = all.joinToString("   ") { (id, ic) ->
                if (GameHost.codexBiomesSeen().contains(id)) ic else "·"
            }
            textSize = 14f; setPadding(0, dp(2), 0, 0)
        })

        body.addView(head("— FOLK MET —"))
        body.addView(TextView(context).apply {
            val names = mapOf("GIFT" to "🕯️ gift-bearers", "HEALER" to "🧙 healers",
                "LORE" to "📖 lorekeepers", "SMITH" to "⚒️ smiths",
                "MAPKEEPER" to "🗺️ mapkeepers", "MERCHANT" to "🪙 merchants",
                "GAMBLER" to "🎲 gamblers", "GHOST" to "👻 the fallen")
            text = names.entries.joinToString("  ") { (k, n) ->
                if (GameHost.codexMetKinds().contains(k)) n else "·"
            }
            textSize = 12f; setPadding(0, dp(2), 0, 0)
        })

        body.addView(head("— NOTABLE FINDS —"))
        val finds = GameHost.codexItemNames()
        body.addView(TextView(context).apply {
            text = if (finds.isEmpty()) "(nothing worthy of record yet)"
            else finds.takeLast(12).joinToString("\n") { "✦ $it" }
            textSize = 11f; typeface = Typeface.MONOSPACE
            setTextColor(0xFFE0B84F.toInt()); setPadding(0, dp(2), 0, 0)
        })

        root.addView(ScrollView(context).apply { addView(body) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        root.addView(TextView(context).apply {
            text = "✕ CLOSE"; textSize = 12f; gravity = android.view.Gravity.CENTER
            typeface = Typeface.MONOSPACE; setTextColor(0xFFE9DFC8.toInt())
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(38)))
        setContentView(root)
    }

    private fun renderMonster(body: LinearLayout, m: MType) {
        val known = GameHost.codexIsSeen(m.id)
        val kills = GameHost.codexKillCount(m.id)
        val threat = GameHost.engineRef()?.lore?.threat(m.id) ?: 1f
        val color = when {
            !known -> 0xFF3A322A.toInt()
            threat >= 1.4f -> 0xFFC4483C.toInt()
            threat <= 0.8f -> 0xFF8FA876.toInt()
            else -> 0xFFD9C9A8.toInt()
        }
        body.addView(TextView(context).apply {
            text = if (known)
                "${m.glyph} ${m.name} — slain ×$kills · fear ×%.2f".format(threat)
            else "${m.glyph} ??????????"
            textSize = 11f; typeface = Typeface.MONOSPACE
            setTextColor(color); setPadding(0, dp(2), 0, dp(2))
        })
    }
}
