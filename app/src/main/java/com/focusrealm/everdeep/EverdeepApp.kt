package com.focusrealm.everdeep

import android.app.Application

class EverdeepApp : Application(), androidx.work.Configuration.Provider {

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder(this).build()

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        FocusBus.init(this)
        SoundCues.init(this)
        GameHost.init(this)
    }
}
