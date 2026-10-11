package com.focusrealm.everdeep

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

object FocusBus {

    data class BlockedApp(val pkg: String, val label: String)

    val CATALOG = listOf(
        BlockedApp("com.instagram.android", "Instagram"),
        BlockedApp("com.zhiliaoapp.musically", "TikTok"),
        BlockedApp("com.google.android.youtube", "YouTube"),
        BlockedApp("com.reddit.frontpage", "Reddit"),
        BlockedApp("com.twitter.android", "X (Twitter)"),
        BlockedApp("com.facebook.katana", "Facebook"),
        BlockedApp("com.netflix.mediaclient", "Netflix"),
        BlockedApp("tv.twitch.android.app", "Twitch")
    )

    var monitorActive = false
    val currentApp = MutableStateFlow<String?>(null)
    val blocklist = MutableStateFlow<Set<String>>(emptySet())
    val simPause = MutableStateFlow(false)

    // enforcement + site filtering
    var shield = false
    var shieldSnoozeUntil = 0L
    var sound = true
    var vpnActive = false
    var darkAdvances = false
    var warded = false
    var wardStart = 9
    var wardEnd = 17
    val siteBlocklist = MutableStateFlow<Set<String>>(emptySet())
    val siteHit = MutableStateFlow<String?>(null)
    var onSiteHit: (() -> Unit)? = null
    var onSiteClear: (() -> Unit)? = null

    val DEFAULT_SITES = setOf("instagram.com", "tiktok.com", "x.com", "twitter.com",
        "reddit.com", "youtube.com", "facebook.com", "netflix.com", "twitch.tv")

    private var prefs: SharedPreferences? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("everdeep", Context.MODE_PRIVATE)
        blocklist.value = prefs!!.getStringSet("blocklist", emptySet())?.toSet() ?: emptySet()
        shield = prefs!!.getBoolean("shield", false)
        sound = prefs!!.getBoolean("sound", true)
        siteBlocklist.value = prefs!!.getStringSet("siteBlocklist", DEFAULT_SITES) ?: DEFAULT_SITES
        darkAdvances = prefs!!.getBoolean("darkAdvances", false)
        warded = prefs!!.getBoolean("warded", false)
        wardStart = prefs!!.getInt("wardStart", 9)
        wardEnd = prefs!!.getInt("wardEnd", 17)
    }

    fun toggle(pkg: String) {
        val cur = blocklist.value.toMutableSet()
        if (!cur.add(pkg)) cur.remove(pkg)
        blocklist.value = cur
        prefs?.edit()?.putStringSet("blocklist", cur)?.apply()
    }

    fun add(pkg: String) {
        val cur = blocklist.value.toMutableSet()
        cur.add(pkg)
        blocklist.value = cur
        prefs?.edit()?.putStringSet("blocklist", cur)?.apply()
    }

    fun setShield(v: Boolean) { shield = v; prefs?.edit()?.putBoolean("shield", v)?.apply() }
    fun setSound(v: Boolean)  { sound = v;  prefs?.edit()?.putBoolean("sound", v)?.apply() }
    fun setDark(v: Boolean)   { darkAdvances = v; prefs?.edit()?.putBoolean("darkAdvances", v)?.apply() }

    fun setWard(on: Boolean, s: Int, e: Int) {
        warded = on; wardStart = s.coerceIn(0, 23); wardEnd = e.coerceIn(1, 24)
        prefs?.edit()?.putBoolean("warded", warded)
            ?.putInt("wardStart", wardStart)?.putInt("wardEnd", wardEnd)?.apply()
    }

    fun addSite(d: String) {
        siteBlocklist.value = siteBlocklist.value + d.lowercase()
        prefs?.edit()?.putStringSet("siteBlocklist", siteBlocklist.value)?.apply()
    }

    fun labelFor(pkg: String): String =
        CATALOG.firstOrNull { it.pkg == pkg }?.label
            ?: pkg.substringAfterLast('.').ifEmpty { pkg }

    fun isBlockedNow(): Boolean {
        if (simPause.value || siteHit.value != null) return true
        if (!monitorActive) return false
        val app = currentApp.value ?: return false
        return blocklist.value.contains(app)
    }

    fun siteMatches(domain: String): Boolean =
        siteBlocklist.value.any { domain == it || domain.endsWith(".$it") }

    fun notifySiteHit(domain: String) { siteHit.value = domain; onSiteHit?.invoke() }

    fun inWardWindow(): Boolean {
        if (!warded) return false
        val c = java.util.Calendar.getInstance()
        val day = c.get(java.util.Calendar.DAY_OF_WEEK)
        if (day < java.util.Calendar.MONDAY || day > java.util.Calendar.FRIDAY) return false
        val h = c.get(java.util.Calendar.HOUR_OF_DAY)
        return if (wardStart < wardEnd) h in wardStart until wardEnd
               else h >= wardStart || h < wardEnd
    }

    fun previewPause() {
        simPause.value = true
        handler.postDelayed({ simPause.value = false }, 4000)
    }
}
