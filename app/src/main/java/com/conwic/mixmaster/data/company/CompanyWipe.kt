package com.conwic.mixmaster.data.company

import android.content.Context
import android.content.Intent
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.data.db.AppDatabase
import com.conwic.mixmaster.data.db.DATABASE_NAME
import java.io.File

/**
 * Takes a company's data off this phone: the database, the photos, a count or a mix left half
 * done — everything but the phone's own settings (language, theme, the alarm sound).
 *
 * Done when the server says this phone has no place in the company any more (the employer took
 * the person off, or gave them a new code for another phone), and when a worker leaves. The app
 * then starts again from scratch, as on the day it was installed, with a note to say why.
 */
object CompanyWipe {

    enum class Reason { Revoked, Left }

    /** What goes with the company. The language, theme and alarm-sound files stay. */
    private val CompanyPrefs = listOf("mixmaster_stock_count", "mixmaster_mix_run", "mixmaster_sync")

    fun run(context: Context, reason: Reason) {
        val app = context.applicationContext
        val name = CompanyStore.current(app)?.companyName.orEmpty()
        SyncEngine.stop()
        // Written down first: whatever happens after this, the phone must not come back up still
        // holding the key, and the person should be told why their data is gone.
        if (reason == Reason.Revoked) CompanyStore.setEnded(app, name)
        CompanyStore.clear(app)
        runCatching { AppDatabase.closeAndReset() }
        app.deleteDatabase(DATABASE_NAME)
        File(app.filesDir, "photos").deleteRecursively()
        CompanyPrefs.forEach { file ->
            app.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
        restart(app)
    }

    /**
     * Everything in memory still holds the old data — every screen, every view model — so the
     * process goes, and the app comes back up on the new, empty database.
     */
    private fun restart(app: Context) {
        runCatching {
            val intent = Intent(app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            app.startActivity(intent)
        }
        Runtime.getRuntime().exit(0)
    }
}
