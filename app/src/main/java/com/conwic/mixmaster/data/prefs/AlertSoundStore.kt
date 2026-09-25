package com.conwic.mixmaster.data.prefs

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/**
 * Which sound the end of a batch makes.
 *
 * SharedPreferences rather than DataStore, for the same reason the language is: the alarm goes
 * off inside a broadcast receiver, which has nowhere to suspend, and the notification channel
 * has to be built with the sound already in hand.
 */
object AlertSoundStore {

    private const val FILE = "mixmaster_alert"
    private const val KEY = "uri"

    /** Written for a chosen silence, which is not the same as never having chosen at all. */
    private const val SILENT = "silent"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun raw(context: Context): String? =
        runCatching { prefs(context).getString(KEY, null) }.getOrNull()

    /** What was picked, or null where nothing was — which means the phone's own alarm. */
    fun chosen(context: Context): Uri? = runCatching {
        raw(context)?.takeIf { it.isNotBlank() && it != SILENT }?.let(Uri::parse)
    }.getOrNull()

    fun isSilent(context: Context): Boolean = raw(context) == SILENT

    /** Null is silence, chosen on purpose. Not writing at all leaves the phone's own alarm. */
    fun write(context: Context, uri: Uri?) {
        runCatching { prefs(context).edit().putString(KEY, uri?.toString() ?: SILENT).commit() }
    }

    /** What to ring: what was picked, else the phone's alarm, else its notification tone. */
    fun uri(context: Context): Uri? {
        if (isSilent(context)) return null
        chosen(context)?.let { return it }
        return deviceAlarm()
    }

    /** The phone's own, which is what the app rings until somebody says otherwise. */
    fun deviceAlarm(): Uri? = runCatching {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }.getOrNull()

    /**
     * A short, stable name for whatever is set.
     *
     * A notification channel keeps the sound it was created with for as long as it exists —
     * calling createNotificationChannel again with a new sound does nothing at all. So changing
     * the sound means a new channel, and that means the channel's id has to be derived from the
     * sound rather than fixed. Which has the side benefit that a channel somebody has already
     * silenced by hand in the phone's settings is left behind rather than fought with.
     */
    fun key(context: Context): String {
        val stored = raw(context) ?: return "default"
        return if (stored == SILENT) "silent" else Integer.toHexString(stored.hashCode())
    }

    /** What the sound is called, for the line under the setting. */
    fun title(context: Context): String? = runCatching {
        uri(context)?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
    }.getOrNull()
}
