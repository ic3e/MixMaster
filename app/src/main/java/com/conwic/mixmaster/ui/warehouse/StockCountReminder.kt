package com.conwic.mixmaster.ui.warehouse

import com.conwic.mixmaster.data.company.CompanyStore
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.prefs.LanguageStore
import com.conwic.mixmaster.data.prefs.StockCountStore
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.nextCountReminderAt
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId

/**
 * The nudge to count the shed.
 *
 * Booked with the system clock for the chosen time (eight unless changed) on the day a count
 * falls due, and then every third day until one is done. Not an exact alarm and not a loud one: this is a
 * reminder, not a batch going off, and it may land a few minutes late without anybody minding.
 *
 * Booked again whenever anything it depends on changes — the interval, a count finished — and on
 * every app start, restart of the phone and install of a new build, all of which can drop it.
 */
object StockCountReminder {

    private const val CHANNEL = "stock_count"
    private const val NOTIFICATION_ID = 4712
    private const val REQUEST = 8811
    const val EXTRA_OPEN_COUNT = "com.conwic.mixmaster.OPEN_STOCK_COUNT"

    /** Set when the notification is what opened the app; the navigation takes it from here. */
    val openRequested = MutableStateFlow(false)

    fun schedule(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmIntent(context)
        runCatching { manager.cancel(pending) }
        // In a company only the phones allowed to change the shelf are asked to count it.
        val link = CompanyStore.current(context)
        if (link != null && !link.owner && !link.perms.warehouse) return
        val state = StockCountStore.read(context)
        val dueAt = state.dueAt() ?: return
        val at = nextCountReminderAt(
            dueAt, state.notifiedAt, System.currentTimeMillis(), ZoneId.systemDefault(), state.reminderMinute,
        )
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
    }

    /** Takes the reminder off the shade — a count has just been finished. */
    fun dismiss(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    internal fun fire(context: Context) {
        val now = System.currentTimeMillis()
        val state = StockCountStore.read(context)
        // Counted in the meantime, or switched off: nothing to say, only the next one to book.
        if (state.isDue(now)) {
            notify(context, state.lastCountAt)
            StockCountStore.markNotified(context, now)
        }
        schedule(context)
    }

    private fun notify(context: Context, lastCountAt: Long) {
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // The app's language, not the phone's: a receiver's context knows nothing of the setting.
        val words = LanguageStore.wrap(context)
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                words.getString(R.string.sc_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = words.getString(R.string.sc_channel_why) },
        )
        val text = if (lastCountAt > 0L) {
            val day = Instant.ofEpochMilli(lastCountAt).atZone(ZoneId.systemDefault()).toLocalDate()
            words.getString(R.string.sc_notify_text, formatDueDate(day))
        } else {
            words.getString(R.string.sc_notify_text_never)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_mix_timer)
            .setContentTitle(words.getString(R.string.sc_notify_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, StockCountReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST + 1,
            Intent(context, MainActivity::class.java).apply {
                // Brought forward rather than rebuilt, as the mixing alarm does — a batch may be
                // running behind it.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_COUNT, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Called with the intent that opened or reached the activity. */
    fun takeOpenRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_COUNT, false) != true) return
        intent.removeExtra(EXTRA_OPEN_COUNT)
        openRequested.value = true
    }
}

/** What the system clock calls back into on the morning a count is due. */
class StockCountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StockCountReminder.fire(context)
    }
}

/** A restart of the phone, or a new build installed over this one, drops every booked alarm. */
class StockCountRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StockCountReminder.schedule(context)
    }
}
