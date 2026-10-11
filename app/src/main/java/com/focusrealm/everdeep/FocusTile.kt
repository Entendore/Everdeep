package com.focusrealm.everdeep

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FocusTile : TileService() {

    companion object {
        fun sync(ctx: Context) {
            try { requestListeningState(ctx, ComponentName(ctx, FocusTile::class.java)) }
            catch (_: Exception) {}
        }
    }

    override fun onStartListening() { render() }

    override fun onClick() {
        FocusBus.init(applicationContext)
        GameHost.init(applicationContext)
        if (GameHost.running.value) GameHost.stopSession() else GameHost.startSession()
        render()
    }

    private fun render() {
        val t = tile ?: return
        if (GameHost.running.value) {
            t.state = Tile.STATE_ACTIVE
            t.label = "Everdeep · focused"
            t.subtitle = "the hero delves"
        } else {
            t.state = Tile.STATE_INACTIVE
            t.label = "Everdeep"
            t.subtitle = "session idle"
        }
        t.updateTile()
    }
}
