package com.focusrealm.everdeep

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusrealm.everdeep.game.Tone

class ChronicleDialog(context: Context) : Dialog(context) {

    private val TONE = mapOf(
        Tone.PLAIN to 0xFF8D8168.toInt(), Tone.GOOD to 0xFF8FA876.toInt(),
        Tone.BAD to 0xFFC4483C.toInt(), Tone.EPIC to 0xFFF0A35C.toInt())

    private lateinit var replayBtn: TextView
    private lateinit var speedBtn: TextView

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun chip(text: String): TextView = TextView(context).apply {
        this.text = text; textSize = 12f; gravity = android.view.Gravity.CENTER
        typeface = Typeface.MONOSPACE; setTextColor(0xFFE9DFC8.toInt())
        setPadding(dp(12), 0, dp(12), 0)
        background = GradientDrawable().apply {
            setColor(0xFF14100C.toInt()); setStroke(dp(1), 0xFF2A221A.toInt())
            cornerRadius = dp(4f) }
    }

    override fun show() {
        super.show()
        window?.setBackgroundDrawable(ColorDrawable(0xFF0B0908.toInt()))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.82f).toInt())
    }

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(TextView(context).apply {
            text = "📜 THE CHRONICLE"; textSize = 14f
            typeface = Typeface.MONOSPACE; letterSpacing = 0.2f
            setTextColor(0xFFD98E4A.toInt())
        })

        val saga = GameHost.sagaFlow.value
        if (saga.isNotEmpty()) {
            root.addView(TextView(context).apply {
                text = "— the saga —"; textSize = 10f; typeface = Typeface.MONOSPACE
                setTextColor(0xFF6B6150.toInt()); setPadding(0, dp(10), 0, dp(4))
            })
            for (line in saga) root.addView(TextView(context).apply {
                text = line; textSize = 11.5f; typeface = Typeface.MONOSPACE
                setTextColor(0xFFE9DFC8.toInt()); setLineSpacing(dp(2), 1f)
            })
        }

        val fallen = GameHost.fallenHall.value
        if (fallen.isNotEmpty()) {
            root.addView(TextView(context).apply {
                text = "— hall of the fallen —"; textSize = 10f
                typeface = Typeface.MONOSPACE; setTextColor(0xFF6B6150.toInt())
                setPadding(0, dp(12), 0, dp(4))
            })
            for (line in fallen.take(5)) root.addView(TextView(context).apply {
                text = "⚰️ $line"; textSize = 10.5f; typeface = Typeface.MONOSPACE
                setTextColor(0xFFB8A98C.toInt()); setPadding(0, dp(2), 0, dp(2))
            })
        }

        root.addView(TextView(context).apply {
            text = "— the record, turn by turn —"; textSize = 10f
            typeface = Typeface.MONOSPACE; setTextColor(0xFF6B6150.toInt())
            setPadding(0, dp(12), 0, dp(4))
        })
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val hist = GameHost.historySnapshot()
        for (l in hist) {
            list.addView(TextView(context).apply {
                text = "T${l.turn} · ${l.text}" + (if (l.n > 1) " ×${l.n}" else "")
                textSize = 10.5f; typeface = Typeface.MONOSPACE
                setTextColor(TONE[l.tone] ?: 0xFF8D8168.toInt())
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        if (hist.isEmpty()) list.addView(TextView(context).apply {
            text = "(nothing yet — the run is young)"; textSize = 11f
            typeface = Typeface.MONOSPACE; setTextColor(0xFF6B6150.toInt()) })
        val scroll = ScrollView(context).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })

        replayBtn = chip(if (GameHost.playing.value) "⏹ STOP" else "▶ REPLAY RUN")
        speedBtn = chip("SPEED ${260 / GameHost.playbackSpeedMs}×")
        val share = chip("↗")
        share.setOnClickListener {
            val sb = StringBuilder("📜 EVERDEEP — the saga\n\n")
            GameHost.sagaFlow.value.forEach { sb.appendLine(it) }
            val r = GameHost.recordsFlow.value
            sb.appendLine().appendLine(
                "best floor ${r.bestDepth} · heroes fallen ${r.heroesFallen}" +
                " · slain ${r.totalKills} · 🧬 g${r.generation}")
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString())
                }, "Share the saga"))
        }
        val close = chip("✕")
        close.setOnClickListener { dismiss() }
        replayBtn.setOnClickListener {
            if (GameHost.playing.value) GameHost.stopPlayback()
            else { GameHost.startPlayback(); dismiss() }
        }
        speedBtn.setOnClickListener {
            GameHost.playbackSpeedMs = when (GameHost.playbackSpeedMs) {
                260 -> 130; 130 -> 65; else -> 260
            }
            speedBtn.text = "SPEED ${260 / GameHost.playbackSpeedMs}×"
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(replayBtn, LinearLayout.LayoutParams(0, dp(38), 1.2f))
        row.addView(speedBtn, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(8) })
        row.addView(share, LinearLayout.LayoutParams(0, dp(38), 0.6f).apply { marginStart = dp(8) })
        row.addView(close, LinearLayout.LayoutParams(0, dp(38), 0.6f).apply { marginStart = dp(8) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        setContentView(root)
    }
}
