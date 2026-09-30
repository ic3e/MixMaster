package com.conwic.mixmaster.ui.breaktime

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.conwic.mixmaster.R

/**
 * "Send to a friend" in the copy from Google Play: the link to Pour Day on Google Play. Play allows
 * no app to carry another app's APK in its files, so this copy carries none, and the friend gets
 * the game from Play the same way.
 */
object PourDayShare {

    /** What goes with it: the message with Pour Day's Google Play address in it. */
    @StringRes val message: Int = R.string.break_time_send_link

    suspend fun share(context: Context, subject: String, text: String, chooser: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, chooser)) }
    }
}
