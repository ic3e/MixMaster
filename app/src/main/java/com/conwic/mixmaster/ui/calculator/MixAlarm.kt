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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.conwic.mixmaster.MainActivity
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.prefs.LanguageStore
import com.conwic.mixmaster.data.prefs.MixRunStore

/**
 * The batch is done even when nobody is looking at the phone.
 *
 * A mixing timer that only rings while its screen is up is the one that gets missed: the phone
 * goes in a pocket, a call comes in, the screen times out. So the end of a batch is also booked
 * with the system clock — the same way an alarm clock is, which is the one kind of alarm the
 * phone will not sleep through — and it comes back as a full-screen alert that lights the
 * screen and opens the app on the batch that finished.
 *
 * A restart clears the booking, and so does installing a new build of this app — which on this
 * phone happens several times a day. [MixRestartReceiver] books it again from the run written
 * down on disk.
 */
object MixAlarm {

    const val EXTRA_FROM_ALARM = "com.conwic.mixmaster.FROM_MIX_ALARM"
    private const val EXTRA_TITLE = "com.conwic.mixmaster.MIX_TITLE"
    private const val CHANNEL = "mixing"
    private const val NOTIFICATION_ID = 4711
    private const val REQUEST = 8801

    /** Long-short-long, the pattern used both here and by the screen's own alert. */
    val VibratePattern = longArrayOf(0, 500, 250, 500, 250, 500)

    /** Books the end of this batch with the system clock. */
    fun schedule(context: Context, atMillis: Long, title: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmIntent(context, title)
        runCatching {
            if (canBeExact(context)) {
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

    /**
     * Ended, acknowledged, or the screen has taken over the shouting: the clock has nothing
     * left to say.
     *
     * Both halves go — the booking *and* anything already on the shade. Cancelling only the
     * notification used to leave the booking standing, and it would then go off in the middle
     * of the next batch.
     */
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

    /** Whether the phone will let this land to the second, or only around then. */
    fun canBeExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /**
     * Whether an alert may put itself on the screen, rather than only on the shade.
     *
     * From Android 14 the permission is granted per app by the system and can be taken away,
     * and the mixing screen says so rather than promising an alarm it cannot ring.
     */
    fun canAlertOverLockScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
    }

    /** The phone's alarm sound, or its notification sound where there is none. */
    fun alarmSoundUri(): Uri? = runCatching {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }.getOrNull()

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
                // Brought forward, not rebuilt. CLEAR_TOP is what reaches past whatever is
                // standing in front of the app — a camera, a dialler — and SINGLE_TOP is what
                // stops it destroying the activity on the way: together, on a singleTop
                // activity, they hand the intent to the one that is already running, and the
                // batch, the clock and the calculator behind it are all still there.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_FROM_ALARM, true)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Rings, buzzes, lights the screen, and opens the app where it left off. */
    fun alert(context: Context, title: String) {
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // The app's own language, not the phone's: this runs in a receiver, whose context knows
        // nothing about the setting, and an Estonian site does not want an English alarm.
        val words = LanguageStore.wrap(context)
        val channel = NotificationChannel(
            CHANNEL,
            words.getString(R.string.mix_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = words.getString(R.string.mix_channel_why)
            enableVibration(true)
            vibrationPattern = VibratePattern
            setBypassDnd(true)
            setSound(
                alarmSoundUri(),
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
            .setContentTitle(words.getString(R.string.mix_ready))
            .setContentText(title.ifBlank { words.getString(R.string.mix_channel) })
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

    /** The batch this alarm was booked for, as it was named on the screen. */
    fun titleFrom(intent: Intent): String = intent.getStringExtra(EXTRA_TITLE).orEmpty()
}

/** What the system clock calls back into when a batch is up. */
class MixAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MixAlarm.alert(context, MixAlarm.titleFrom(intent))
    }
}

/**
 * Books the batch again after the phone restarts, or after this app is replaced under a run.
 *
 * Both of those throw away every alarm the app had booked, and the second one happens here
 * whenever a new build is installed — which on the phone this is written for can be in the
 * middle of mixing. The run itself is on disk, so the deadline is still known.
 *
 * A deadline that has already gone by is left alone: the screen shows the batch as up the moment
 * the app is opened, and an alarm going off hours later says nothing true.
 */
class MixRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val run = MixRunStore.read(context) ?: return
        val progress = MixRunStore.progress(context)
        if (progress.phase != "RUNNING") return
        if (progress.deadline <= System.currentTimeMillis()) return
        MixAlarm.schedule(context, progress.deadline, run.title)
    }
}
