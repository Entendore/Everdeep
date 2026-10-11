package com.focusrealm.everdeep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("pkg") ?: return
        FocusBus.init(context)
        FocusBus.add(pkg)
        KeepAliveService.instance?.update("⛓️ ${FocusBus.labelFor(pkg)} chained — the hero presses on")
        try { EverdeepWidget.update(context, "Everdeep", "⛓️ ${FocusBus.labelFor(pkg)} blocked") } catch (_: Exception) {}
    }
}
