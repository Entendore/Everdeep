package com.focusrealm.everdeep

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.focusrealm.everdeep.game.Feats
import com.focusrealm.everdeep.game.Legacy
import com.focusrealm.everdeep.game.Talent

/** Embers, legacy talents, the ledger, vows, feats — and export/import. */
class LegacyDialog(context: Context) : Dialog(context) {

    private val body = LinearLayout(context)
    private val blocks = "▁▂▃▄▅▆▇█"

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun chip(text: String, enabled: Boolean = true): TextView = TextView(context).apply {
        this.text = text; textSize = 11f; gravity = Gravity.CENTER
        typeface = Typeface.MONOSPACE
        setTextColor(if (enabled) 0xFFE9DFC8.toInt() else 0xFF4A4036.toInt())
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            setColor(if (enabled) 0xFF14100C.toInt() else 0xFF0E0B08.toInt())
            setStroke(dp(1), if (enabled) 0xFF2A221A.toInt() else 0xFF1A1510.toInt())
            cornerRadius = dp(4f)
        }
    }

    private fun label(text: String, size: Float = 10f, color: Int = 0xFF6B6150.toInt()) =
        TextView(context).apply {
            this.text = text; textSize = size; typeface = Typeface.MONOSPACE
            setTextColor(color); setPadding(0, dp(4), 0, 0)
        }

    override fun show() {
        super.show()
        window?.setBackgroundDrawable(ColorDrawable(0xFF0B0908.toInt()))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.84f).toInt())
    }

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(TextView(context).apply {
            text = "🔥 THE LINEAGE'S LEGACY"; textSize = 14f
            typeface = Typeface.MONOSPACE; letterSpacing = 0.15f
            setTextColor(0xFFD98E4A.toInt())
        })
        root.addView(ScrollView(context).apply { addView(body) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        root.addView(chip("✕ CLOSE").apply { setOnClickListener { dismiss() } },
            LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(10) })
        setContentView(root)
        rebuild()
    }

    private fun rebuild() {
        body.removeAllViews()

        // ---------- embers + talents ----------
        body.addView(label("🔥 ${GameHost.embersFlow.value} embers — 1 per floor, 3 per warden", 11f,
            0xFFE0B84F.toInt()).apply { setPadding(0, dp(4), 0, dp(8)) })
        for (t in Talent.entries) {
            val rank = GameHost.talentsFlow.value[t.id] ?: 0
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6))
            }
            col.addView(TextView(context).apply {
                text = "${t.name}  ${"●".repeat(rank)}${"○".repeat(t.maxRank - rank)}"
                textSize = 12f; typeface = Typeface.MONOSPACE; setTextColor(0xFFE9DFC8.toInt())
            })
            col.addView(label(t.desc))
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val canBuy = rank < t.maxRank && GameHost.embersFlow.value >= t.cost(rank)
            row.addView(chip(if (rank >= t.maxRank) "MAX" else "🔥 ${t.cost(rank)}", canBuy).apply {
                setOnClickListener { if (GameHost.buyTalent(t.id)) rebuild() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            body.addView(row)
        }

        // ---------- the ledger ----------
        body.addView(label("\n📖 THE LEDGER — your side of the bargain"))
        body.addView(label(
            "🔒 everything on this screen is computed and stored on this device. " +
            "Nothing is uploaded, ever — the only thing that leaves is a manual ↗ export.",
            10f, 0xFF6B6150.toInt()))
        body.addView(label("🔥 focus streak: ${GameHost.streakFlow.value} day(s) — " +
            "10+ focused minutes earns a day", 11.5f, 0xFFE9DFC8.toInt()))
        body.addView(TextView(context).apply {
            val c = GameHost.curveFlow.value
            text = if (c.isEmpty()) "🧬 line fitness: (no lines scored yet)"
            else "🧬 line fitness: " + c.takeLast(16).joinToString("") {
                blocks[((it / (c.max().takeIf { m -> m > 0f } ?: 1f)) *
                    (blocks.length - 1)).toInt().coerceIn(0, 7)]
            }
            textSize = 11.5f; typeface = Typeface.MONOSPACE
            setTextColor(0xFFB39DDB.toInt()); setPadding(0, dp(4), 0, 0)
        })
        body.addView(TextView(context).apply {
            val h = GameHost.heatmap()
            val mx = (h.max() ?: 0L).coerceAtLeast(1L)
            text = "⏰ when you slip (00→24h): " + h.joinToString("") {
                if (it <= 0L) "·"
                else blocks[((it.toFloat() / mx) * (blocks.length - 1)).toInt().coerceIn(0, 7)]
            }
            textSize = 11.5f; typeface = Typeface.MONOSPACE
            setTextColor(0xFF7FB4C9.toInt()); setPadding(0, dp(4), 0, 0)
        })
        for ((appName, sec) in GameHost.ledgerFlow.value) {
            val t = if (sec < 60) "${sec}s" else if (sec < 3600) "${sec / 60}m"
                    else "${sec / 3600}h ${(sec % 3600) / 60}m"
            body.addView(TextView(context).apply {
                text = "⏳ $appName — $t distracted"
                textSize = 11f; typeface = Typeface.MONOSPACE
                setTextColor(if (sec > 1800) 0xFFC4483C.toInt() else 0xFF8D8168.toInt())
                setPadding(0, dp(4), 0, 0)
            })
        }

        // ---------- vows ----------
        body.addView(label("\nTODAY'S VOWS — your focus, paid to the hero directly"))
        for (v in GameHost.vowsFlow.value) {
            body.addView(TextView(context).apply {
                text = (if (v.done) "✅ " else "▫️ ") + v.name + " — " +
                       (if (v.done) "kept" else "${v.progress}/${v.goal} ${v.unit}")
                textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(if (v.done) 0xFF8FA876.toInt() else 0xFFE9DFC8.toInt())
                setPadding(0, dp(6), 0, 0)
            })
            body.addView(label("${v.desc} · pays: ${Legacy.boonName(v.boon)}"))
        }

        // ---------- feats ----------
        body.addView(label("\n🏅 FEATS OF THE LINEAGE"))
        for (f in Feats.ALL) {
            val got = GameHost.featsDone.contains(f.id)
            body.addView(TextView(context).apply {
                text = (if (got) "🏅 " else "▫️ ") + f.name + "  ·  +${f.embers} 🔥"
                textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(if (got) 0xFFE0B84F.toInt() else 0xFF4A4036.toInt())
                setPadding(0, dp(6), 0, 0)
            })
            body.addView(label(f.desc))
        }

        // ---------- export / import ----------
        val io = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        io.addView(chip("↗ EXPORT", true).apply {
            setOnClickListener {
                val json = GameHost.exportLineage()
                if (json != null) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("everdeep-lineage", json))
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, json)
                        }, "Export the lineage"))
                }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        io.addView(chip("↧ IMPORT", true).apply {
            setOnClickListener { importPrompt() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        body.addView(io, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun importPrompt() {
        val input = EditText(context).apply {
            hint = "paste an exported lineage here"; textSize = 11f
            typeface = Typeface.MONOSPACE; setSingleLine(false); minLines = 3
        }
        AlertDialog.Builder(context)
            .setTitle("Import lineage")
            .setView(input)
            .setPositiveButton("IMPORT") { _, _ ->
                val ok = GameHost.importLineage(input.text.toString().trim())
                Toast.makeText(context,
                    if (ok) "lineage imported — restart Everdeep to load it"
                    else "that didn't parse as a lineage", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("CANCEL", null).show()
    }
}
