package com.pilltack.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pilltack.app.AlarmActivity
import com.pilltack.app.MainActivity
import com.pilltack.app.R
import com.pilltack.app.data.Dose
import com.pilltack.app.data.Fmt
import com.pilltack.app.data.Medicine

/**
 * Alarm + notification helpers.
 * One alarm per medicine (request code = medicine id, FLAG_UPDATE_CURRENT) so rescheduling
 * replaces the old alarm instead of stacking duplicates. Notification id = dose id.
 */
object Reminders {
    const val CH = "pilltack_reminders"
    const val CH_ALARM = "pilltack_alarm"
    const val CH_REFILL = "pilltack_refill"
    const val FIRE = "com.pilltack.app.FIRE"
    const val SNOOZE_FIRE = "com.pilltack.app.SNOOZE_FIRE"
    const val TAKEN = "com.pilltack.app.TAKEN"
    const val SKIP = "com.pilltack.app.SKIP"
    const val SNOOZE = "com.pilltack.app.SNOOZE"
    const val TEST_FIRE = "com.pilltack.app.TEST_FIRE"
    const val TEST_ID = 9_999_999L

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Time-to-take-your-medicine alerts"; enableVibration(true)
        })
        // Alarm-style channel: alarm ringtone, strong vibration, shown on the lock screen.
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "Alarm-style reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings and opens a full-screen call-style reminder"
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 400, 700, 400, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_REFILL, "Refill reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pi(ctx: Context, code: Int, action: String, vararg extras: Pair<String, Long>): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
        extras.forEach { i.putExtra(it.first, it.second) }
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    fun canFullScreen(ctx: Context) = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun schedule(ctx: Context, p: PendingIntent, at: Long) {
        // Without the exact-alarm permission Android may delay this by minutes; Settings explains how to allow it.
        if (canExact(ctx)) am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    fun setMedAlarm(ctx: Context, medId: Long, at: Long) =
        schedule(ctx, pi(ctx, medId.toInt(), FIRE, "med" to medId, "at" to at), at)

    fun cancel(ctx: Context, medId: Long) = am(ctx).cancel(pi(ctx, medId.toInt(), FIRE))

    fun setSnooze(ctx: Context, doseId: Long, at: Long) =
        schedule(ctx, pi(ctx, (1_000_000 + doseId).toInt(), SNOOZE_FIRE, "dose" to doseId), at)

    fun cancelSnooze(ctx: Context, doseId: Long) =
        am(ctx).cancel(pi(ctx, (1_000_000 + doseId).toInt(), SNOOZE_FIRE))

    /** Fires a sample alarm in 10 seconds so you can lock the phone and see the call-style screen. */
    fun scheduleTest(ctx: Context) =
        schedule(ctx, pi(ctx, 9_000_001, TEST_FIRE), System.currentTimeMillis() + 10_000)

    fun dismiss(ctx: Context, doseId: Long) = NotificationManagerCompat.from(ctx).cancel(doseId.toInt())

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, m: Medicine, d: Dose, snoozeMin: Int, h24: Boolean, alarm: Boolean) =
        post(ctx, d.id, m.name, "Scheduled time: ${Fmt.time(d.scheduledAt, h24)}", snoozeMin, alarm)

    fun showTest(ctx: Context, snoozeMin: Int, alarm: Boolean) =
        post(ctx, TEST_ID, "Test medicine", "This is a test reminder", snoozeMin, alarm)

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Long, name: String, whenText: String, snoozeMin: Int, alarm: Boolean) {
        if (!allowed(ctx)) return
        channels(ctx)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), flags)
        val alarmScreen = PendingIntent.getActivity(ctx, (6_000_000 + id).toInt(),
            Intent(ctx, AlarmActivity::class.java).putExtra("dose", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
        val b = NotificationCompat.Builder(ctx, if (alarm) CH_ALARM else CH)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText("Time to take your medicine!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Time to take your medicine!\nMedicine: $name\n$whenText"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFF164F7A.toInt())
            .setContentIntent(if (alarm) alarmScreen else openApp)
            .addAction(0, "Taken", pi(ctx, (2_000_000 + id).toInt(), TAKEN, "dose" to id))
            .addAction(0, "Snooze $snoozeMin min", pi(ctx, (4_000_000 + id).toInt(), SNOOZE, "dose" to id))
            .addAction(0, "Skip", pi(ctx, (3_000_000 + id).toInt(), SKIP, "dose" to id))
        if (alarm) {
            b.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(alarmScreen, true)   // opens the call-style screen on a locked/off screen
                .setOngoing(true)                          // can't be swiped away by accident
                .setTimeoutAfter(5 * 60_000L)              // stops ringing after 5 minutes if ignored
        } else {
            b.setCategory(NotificationCompat.CATEGORY_REMINDER).setOnlyAlertOnce(true)
        }
        val n = b.build()
        if (alarm) n.flags = n.flags or Notification.FLAG_INSISTENT   // keep ringing until you respond
        NotificationManagerCompat.from(ctx).notify(id.toInt(), n)
    }

    @SuppressLint("MissingPermission")
    fun refillNotice(ctx: Context, m: Medicine) {
        if (!allowed(ctx)) return
        channels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_REFILL)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle("Time to refill")
            .setContentText("${m.name}: only ${m.pillsLeft} left")
            .setColor(0xFF164F7A.toInt()).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify((5_000_000 + m.id).toInt(), n)
    }
}
