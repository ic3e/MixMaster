package com.conwic.mixmaster.data.company

import android.content.Context
import com.conwic.mixmaster.data.wipe.PhoneWipe

/**
 * Takes a company's data off this phone.
 *
 * Done when the server says this phone has no place in the company any more (the employer took
 * the person off, or gave them a new code for another phone), and when a worker leaves. The app
 * then starts again from scratch, as on the day it was installed.
 *
 * Taken off by the employer, the phone keeps nothing at all — not the settings, not the sheets
 * or the reports written out of the company's jobs — only a note to say why, and the offer to
 * uninstall the app. A worker who leaves of their own accord keeps the phone's own settings
 * (language, theme, the alarm sound): the work goes, the app is still theirs.
 */
object CompanyWipe {

    enum class Reason { Revoked, Left }

    /** What goes with the company when a worker leaves. The language, theme and alarm-sound files stay. */
    private val CompanyPrefs = listOf("mixmaster_stock_count", "mixmaster_mix_run", "mixmaster_sync")

    fun run(context: Context, reason: Reason) {
        val app = context.applicationContext
        val name = CompanyStore.current(app)?.companyName.orEmpty()
        SyncEngine.stop()
        // Written down first: whatever happens after this, the phone must not come back up still
        // holding the key, and the person should be told why their data is gone.
        if (reason == Reason.Revoked) CompanyStore.setEnded(app, name)
        CompanyStore.clear(app)
        when (reason) {
            // the company file now holds nothing but the note
            Reason.Revoked -> PhoneWipe.everything(app, keepPrefs = setOf(CompanyStore.FILE))
            Reason.Left -> {
                PhoneWipe.data(app)
                CompanyPrefs.forEach { file ->
                    app.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
                }
            }
        }
        PhoneWipe.restart(app)
    }
}
