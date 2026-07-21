package com.saiyanstrong

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Catches any uncaught crash, saves the stack trace, and lets [MainActivity] show it on the next
 * launch (instead of the normal UI, so it can't re-crash before the user sees it). This is how we
 * diagnose crashes on a device without USB logcat.
 */
object CrashReporter {
    private const val PREFS = "crash_reporter"
    private const val KEY = "last_crash"

    /** Install as early as possible in Application.onCreate so even Hilt/startup crashes are caught. */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val text = "Thread: ${thread.name}\nTime: ${System.currentTimeMillis()}\n\n$sw"
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).commit()
            } catch (_: Throwable) {
                // Never let the reporter itself hide the real crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The saved crash trace (null if none), for the crash screen. */
    fun lastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit()
    }
}
