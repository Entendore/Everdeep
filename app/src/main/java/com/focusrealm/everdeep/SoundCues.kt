package com.focusrealm.everdeep

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object SoundCues {
    private var tg: ToneGenerator? = null
    private val h = Handler(Looper.getMainLooper())
    private var app: Context? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    private fun audible(): Boolean {
        if (!FocusBus.sound) return false
        val c = app ?: return true
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (nm != null &&
            nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
            return false
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private fun play(seq: List<Pair<Int, Long>>) {
        if (!audible()) return
        if (tg == null) try { tg = ToneGenerator(AudioManager.STREAM_MUSIC, 35) } catch (_: Exception) { return }
        var t = 0L
        for ((tone, ms) in seq) {
            h.postDelayed({ try { tg?.startTone(tone, ms.toInt()) } catch (_: Exception) {} }, t)
            t += ms + 40
        }
    }

    fun descend()  { play(listOf(ToneGenerator.TONE_PROP_BEEP2 to 120, ToneGenerator.TONE_CDMA_MED to 160)) }
    fun level()    { play(listOf(ToneGenerator.TONE_CDMA_MED to 90, ToneGenerator.TONE_CDMA_HIGH_L to 90,
                                  ToneGenerator.TONE_PROP_ACK to 120)) }
    fun death()    { play(listOf(ToneGenerator.TONE_CDMA_LOW_L to 220, ToneGenerator.TONE_SUP_ERROR to 260)) }
    fun distract() { play(listOf(ToneGenerator.TONE_PROP_BEEP to 90, ToneGenerator.TONE_PROP_BEEP to 90)) }
    fun session()  { play(listOf(ToneGenerator.TONE_CDMA_CONFIRM to 160, ToneGenerator.TONE_CDMA_MED to 160)) }
}
