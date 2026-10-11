package com.focusrealm.everdeep

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

class FocusWatchService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var breathRun: Runnable? = null
    private var countRun: Runnable? = null
    private var breathText: TextView? = null
    private var countdownText: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        FocusBus.monitorActive = true
        FocusBus.onSiteHit = { updateShield() }
        FocusBus.onSiteClear = { updateShield() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.contains("inputmethod", ignoreCase = true)) return
        if (pkg == "com.android.systemui") return
        FocusBus.currentApp.value = pkg
        updateShield()
    }

    override fun onInterrupt() {}
    override fun onRebind(intent: Intent?) { FocusBus.monitorActive = true; super.onRebind(intent) }

    override fun onUnbind(intent: Intent?): Boolean {
        FocusBus.monitorActive = false
        FocusBus.onSiteHit = null
        FocusBus.onSiteClear = null
        hideShield()
        return super.onUnbind(intent)
    }

    private fun updateShield() {
        val should = FocusBus.shield && FocusBus.isBlockedNow() &&
            Settings.canDrawOverlays(this) &&
            System.currentTimeMillis() > FocusBus.shieldSnoozeUntil
        if (should && overlay == null) showShield()
        else if (!should && overlay != null) hideShield()
    }

    private fun showShield() {
        val dp = resources.displayMetrics.density
        fun text(s: String, size: Float, color: Int, ls: Float = 0f) = TextView(this).apply {
            this.text = s; textSize = size; setTextColor(color); letterSpacing = ls
            typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xF20B0908.toInt())
            setPadding((36 * dp).toInt(), 0, (36 * dp).toInt(), 0)
        }
        root.addView(text("⏸", 42f, 0xFFD98E4A.toInt()))
        root.addView(text("THE HERO WAITS", 18f, 0xFFE9DFC8.toInt(), 0.25f))
        val what = FocusBus.siteHit.value?.let { "🌐 $it" }
            ?: FocusBus.labelFor(FocusBus.currentApp.value ?: "")
        root.addView(text("$what is on your blocklist",
            12f, 0xFF8D8168.toInt()).apply { setPadding(0, (10 * dp).toInt(), 0, 0) })
        breathText = text("breathe in — 4", 15f, 0xFFD98E4A.toInt())
            .apply { setPadding(0, (26 * dp).toInt(), 0, 0) }
        root.addView(breathText)
        countdownText = text("hold still — 10", 11f, 0xFF6B6150.toInt())
            .apply { setPadding(0, (8 * dp).toInt(), 0, 0) }
        root.addView(countdownText)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            visibility = View.GONE
        }
        fun chip(s: String, dark: Boolean, onClick: () -> Unit) = TextView(this).apply {
            this.text = s; textSize = 12f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
            setTextColor(if (dark) 0xFF171106.toInt() else 0xFFE9DFC8.toInt())
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(if (dark) 0xFFD98E4A.toInt() else 0xFF14100C.toInt())
                setStroke((1 * dp).toInt(), 0xFF2A221A.toInt()); cornerRadius = 6 * dp }
            setOnClickListener { onClick() }
        }
        actions.addView(chip("↩ BACK TO THE DEEP", true) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hideShield()
        })
        actions.addView(chip("· anyway, 5 min", false) {
            FocusBus.shieldSnoozeUntil = System.currentTimeMillis() + 5 * 60_000
            hideShield()
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = (10 * dp).toInt() })
        root.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (24 * dp).toInt() })
        root.addView(text("the run pauses while the screen is theirs", 10f, 0xFF4A4036.toInt())
            .apply { setPadding(0, (18 * dp).toInt(), 0, 0) })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        try {
            getSystemService(WindowManager::class.java).addView(root, lp)
            overlay = root
        } catch (e: Exception) { return }

        val breath = listOf("breathe in — 4", "hold — 7", "breathe out — 8")
        var idx = -1
        breathRun = object : Runnable {
            override fun run() {
                idx = (idx + 1) % breath.size
                breathText?.text = breath[idx]
                main.postDelayed(this, 2600)
            }
        }
        main.postDelayed(breathRun!!, 2600)
        var left = 10
        countRun = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    actions.visibility = View.VISIBLE
                    countdownText?.text = "your torch is safe — choose"
                    return
                }
                countdownText?.text = "hold still — $left"
                main.postDelayed(this, 1000)
            }
        }
        main.postDelayed(countRun!!, 1000)
    }

    private fun hideShield() {
        breathRun?.let { main.removeCallbacks(it) }; breathRun = null
        countRun?.let { main.removeCallbacks(it) }; countRun = null
        overlay?.let { try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {} }
        overlay = null; breathText = null; countdownText = null
    }
}
