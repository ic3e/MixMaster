package com.conwic.mixmaster.data.wipe

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.data.company.SyncEngine
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.ui.calculator.MixAlarm
import com.conwic.mixmaster.ui.warehouse.StockCountReminder
import java.io.File

/**
 * Takes what MixMaster keeps off this phone.
 *
 * Two depths. [data] is the work — the database, the photos and plans, the stored sheets, the
 * reports and pick-up lists written out of it, the cache — and leaves the phone's own settings,
 * for a worker who leaves a company and goes on using the app. [everything] is all of it, the
 * settings, the lock and the game's saves included: the app as on the day it was installed. It
 * is what "Empty all" does, and what a phone gets when its company takes it off.
 *
 * A backup exported to the phone's own files is the one thing left: it was saved outside the app,
 * and it is the person's way back.
 */
object PhoneWipe {

    fun data(app: Context) {
        SyncEngine.stop()
        // An alarm outlives the data it was booked from: a mix timer or a count reminder left
        // standing would go off on a phone with nothing on it.
        MixAlarm.cancel(app)
        StockCountReminder.cancel(app)
        runCatching { NotificationManagerCompat.from(app).cancelAll() }
        runCatching { AppDatabase.closeAndReset() }
        app.databaseList().forEach { app.deleteDatabase(it) }
        listOf("photos", "demo", "sheets").forEach { File(app.filesDir, it).deleteRecursively() }
        // the reports and pick-up lists go to the app's own folder on the phone's storage
        (listOf(app.cacheDir) + app.externalCacheDirs.filterNotNull() + app.getExternalFilesDirs(null).filterNotNull())
            .forEach(::empty)
    }

    /** Everything, but for the settings files named in [keepPrefs]. */
    fun everything(app: Context, keepPrefs: Set<String> = emptySet()) {
        data(app)
        listOf(app.filesDir, app.noBackupFilesDir).forEach(::empty)
        // app_webview and its like: the game's saves, and whatever else a WebView kept
        app.dataDir.listFiles { file -> file.isDirectory && file.name.startsWith("app_") }
            ?.forEach { it.deleteRecursively() }
        File(app.dataDir, "shared_prefs").listFiles()?.forEach { file ->
            val name = file.name.substringBefore(".xml")
            if (name in keepPrefs) return@forEach
            // Cleared through the API as well as deleted: a file already read is held in memory,
            // and would be written back as it was by the next edit to it.
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            file.delete()
        }
    }

    /**
     * Every screen and view model still holds what has just gone, so the process goes, and the
     * app comes back up on nothing.
     */
    fun restart(app: Context) {
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        Runtime.getRuntime().exit(0)
    }

    private fun empty(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
