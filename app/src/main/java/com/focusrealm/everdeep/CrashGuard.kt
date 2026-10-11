package com.focusrealm.everdeep

import android.app.Application
import android.util.Log
import java.io.File

object CrashGuard {
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(app.filesDir, "last_crash.txt").writeText(
                    "${Log.getStackTraceString(e)}\nthread=${t.name}\n")
            } catch (_: Exception) {}
            previous?.uncaughtException(t, e)
        }
    }
}
