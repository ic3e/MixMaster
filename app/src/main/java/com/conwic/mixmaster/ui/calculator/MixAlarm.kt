package com.conwic.mixmaster.ui.calculator

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.R

/**
 * The batch is done even when nobody is looking at the phone.
 *
 * A mixing timer that only rings while its screen is up is the one that gets missed: the phone
 * goes in a pocket, a call comes in, the screen times out. So the end of a batch is also booked
 * with the system clock — the same way an alarm clock is, which is the one kind of alarm the
 * phone will not sleep through — and it comes back as a full-screen alert that lights the
 * screen and opens the app on the batch that finished.
 */
object MixAlarm {

    const val EXTRA_FROM_ALARM = "com.conwic.mixmaster.FROM_MIX_ALARM"
    private const val EXTRA_TITLE = "title"
    private const val CHANNEL = "mixing"
    private const val NOTIFICATION_ID = 4711
    private const val REQUEST = 8801

    /** Books the end of this batch with the system clock. */
    fun schedule(context: Context, atMillis: Long, title: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmIntent(context, title)
        runCatching {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (exact) {
                // An alarm clock, not a reminder: this one is exempt from the doze the phone
                // drops into with the screen off in a quiet room.
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(atMillis, openAppIntent(context, title)),
                    pending,
                )
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        }
    }

    /** Ended early, or the run is over: the clock has nothing left to say. */
    fun cancel(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            manager?.cancel(alarmIntent(context, ""))
        }
        dismiss(context)
    }

    fun dismiss(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun alarmIntent(context: Context, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, MixAlarmReceiver::class.java).putExtra(EXTRA_TITLE, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context, title: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST + 1,
            Intent(context, MainActivity::class.java).apply {
                // Brought forward, not rebuilt. Clearing the top of the task destroys the
                // activity that is standing on the mixing screen, and with it the batch, the
                // clock and everything typed into the calculator behind it — which is exactly
                // what an alarm must not do.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_FROM_ALARM, true)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Rings, buzzes, lights the screen, and opens the app where it left off. */
    fun alert(context: Context, title: String) {
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.mix_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.mix_channel_why)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        notifications.createNotificationChannel(channel)

        val open = openAppIntent(context, title)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_mix_timer)
            .setContentTitle(context.getString(R.string.mix_ready))
            .setContentText(title.ifBlank { context.getString(R.string.mix_channel) })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(open)
            // Straight onto the screen, lit, over the lock screen — the phone is in a pocket
            // or face down on a bag, and the batch does not wait.
            .setFullScreenIntent(open, true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

/** What the system clock calls back into when a batch is up. */
class MixAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MixAlarm.alert(context, intent.getStringExtra("title").orEmpty())
    }
}
