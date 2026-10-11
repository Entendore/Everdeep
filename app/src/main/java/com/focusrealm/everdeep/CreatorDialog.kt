package com.focusrealm.everdeep

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class CreatorDialog(
    context: Context,
    private val onBegin: (name: String, avatar: String) -> Unit
) : Dialog(context) {

    private val ascii = listOf("@", "&", "Þ", "Ð", "Ω", "Ψ", "Δ", "Σ",
                               "Φ", "Ø", "¶", "§", "¥", "†", "Ŧ", "Ħ")
    private val emoji = listOf("🧝", "🧙", "🧛", "🦊", "🐺", "🐈", "🦉", "🐉",
                               "🗡️", "🛡️", "👑", "💀", "🤖", "🎃", "🧜", "⚔️")

    private var avatar: String = GameHost.heroAvatar
    private var showEmoji = GameHost.heroAvatar.length > 1

    private lateinit var grid: GridLayout
    private lateinit var nameInput: EditText
    private lateinit var preview: TextView
    private lateinit var asciiTab: TextView
    private lateinit var emojiTab: TextView

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun cellBg(selected: Boolean) = GradientDrawable().apply {
        setColor(if (selected) 0xFF241A10.toInt() else 0xFF14100C.toInt())
        setStroke(dp(if (selected) 2 else 1),
                  if (selected) 0xFFD98E4A.toInt() else 0xFF2A221A.toInt())
        cornerRadius = dp(5f)
    }

    override fun show() {
        super.show()
        window?.setBackgroundDrawable(ColorDrawable(0xFF0B0908.toInt()))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        fun title(text: String, size: Int, color: Int, ls: Float = 0.2f) =
            TextView(context).apply {
                this.text = text; textSize = size * 1f; setTextColor(color)
                typeface = Typeface.MONOSPACE; letterSpacing = ls
            }

        root.addView(title("WHO DESCENDS?", 15, 0xFFD98E4A.toInt(), 0.25f))
        root.addView(title("choose a face and a name — the lineage will remember", 10,
            0xFF8D8168.toInt(), 0f).apply { setPadding(0, dp(4), 0, 0) })

        val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        asciiTab = tabButton("ASCII ART")
        emojiTab = tabButton("EMOJI")
        tabs.addView(asciiTab, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(8) })
        tabs.addView(emojiTab, LinearLayout.LayoutParams(0, dp(36), 1f))
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        grid = GridLayout(context).apply {
            columnCount = 4; rowCount = 4; setPadding(0, dp(12), 0, 0)
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))

        nameInput = EditText(context).apply {
            hint = "name your hero (blank = roll one)"
            setTextColor(0xFFE9DFC8.toInt()); setHintTextColor(0xFF6B6150.toInt())
            typeface = Typeface.MONOSPACE; textSize = 13f
            background = GradientDrawable().apply {
                setColor(0xFF1A140E.toInt()); cornerRadius = dp(4f) }
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(24))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = updatePreview()
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })
        }
        val dice = TextView(context).apply {
            text = "🎲"; textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF14100C.toInt()); setStroke(dp(1), 0xFF2A221A.toInt())
                cornerRadius = dp(4f) }
            setOnClickListener { nameInput.setText(GameHost.randomHeroName()); updatePreview() }
        }
        val nameRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        nameRow.addView(nameInput, LinearLayout.LayoutParams(0, -2, 1f))
        nameRow.addView(dice, LinearLayout.LayoutParams(dp(46), -2).apply { marginStart = dp(8) })
        root.addView(nameRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        preview = TextView(context).apply {
            typeface = Typeface.MONOSPACE; textSize = 14f
            setTextColor(0xFFE9DFC8.toInt()); setPadding(0, dp(14), 0, 0)
        }
        root.addView(preview)

        val begin = TextView(context).apply {
            text = "▼ BEGIN THE DESCENT"; textSize = 13f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF171106.toInt())
            letterSpacing = 0.12f
            background = GradientDrawable().apply {
                setColor(0xFFD98E4A.toInt()); cornerRadius = dp(4f) }
            setOnClickListener {
                val name = nameInput.text.toString().trim().ifEmpty { GameHost.randomHeroName() }
                onBegin(name, avatar)
                dismiss()
            }
        }
        root.addView(begin, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(16) })

        setContentView(root)
        rebuildGrid()
        restyleTabs()
        updatePreview()
    }

    private fun tabButton(text: String) = TextView(context).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER
        typeface = Typeface.MONOSPACE; letterSpacing = 0.1f
        setOnClickListener {
            showEmoji = text == "EMOJI"
            rebuildGrid(); restyleTabs()
        }
    }

    private fun restyleTabs() {
        val on = GradientDrawable().apply {
            setColor(0xFF241A10.toInt()); setStroke(dp(2), 0xFFD98E4A.toInt()); cornerRadius = dp(4f) }
        val off = GradientDrawable().apply {
            setColor(0x00000000); setStroke(dp(1), 0xFF2A221A.toInt()); cornerRadius = dp(4f) }
        asciiTab.background = if (!showEmoji) on else off
        emojiTab.background = if (showEmoji) on else off
        asciiTab.setTextColor(if (!showEmoji) 0xFFD98E4A.toInt() else 0xFF8D8168.toInt())
        emojiTab.setTextColor(if (showEmoji) 0xFFD98E4A.toInt() else 0xFF8D8168.toInt())
    }

    private fun rebuildGrid() {
        grid.removeAllViews()
        val options = if (showEmoji) emoji else ascii
        for (g in options) {
            val cell = TextView(context).apply {
                text = g
                textSize = if (g.length > 1) 20f else 17f
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                tag = g
                setOnClickListener { v ->
                    avatar = v.tag.toString()
                    for (i in 0 until grid.childCount) {
                        val c = grid.getChildAt(i) as TextView
                        c.background = cellBg(c.tag.toString() == avatar)
                    }
                    updatePreview()
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = dp(58); height = dp(58)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            grid.addView(cell, lp)
            cell.background = cellBg(g == avatar)
        }
    }

    private fun updatePreview() {
        val nm = nameInput.text.toString().trim().ifEmpty { "(a name will be rolled)" }
        preview.text = "$avatar  $nm"
    }
}
