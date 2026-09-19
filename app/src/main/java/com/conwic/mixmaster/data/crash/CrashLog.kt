package com.conwic.mixmaster.data.crash

import android.content.Context
import com.conwic.mixmaster.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Keeps the stack trace of the last crash so it can be read back on the next launch.
 *
 * The app is tested on a phone on site, where "it closed" is all the report there is — and a
 * stack trace that only exists in logcat may as well not exist. This writes one to a file the
 * next launch can show and copy.
 */
object CrashLog {

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    /** Chains to whatever handler was already installed, so the system still does its part. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file(appContext).writeText(
                    buildString {
                        appendLine("MixMaster ${BuildConfig.VERSION_NAME}")
                        appendLine(LocalDateTime.now().toString())
                        appendLine("thread: ${thread.name}")
                        appendLine()
                        append(trace)
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
