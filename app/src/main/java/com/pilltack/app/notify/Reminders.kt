package com.pilltack.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    const val CH_REFILL = "pilltack_refill"
    const val FIRE = "com.pilltack.app.FIRE"
    const val SNOOZE_FIRE = "com.pilltack.app.SNOOZE_FIRE"
    const val TAKEN = "com.pilltack.app.TAKEN"
    const val SKIP = "com.pilltack.app.SKIP"
    const val SNOOZE = "com.pilltack.app.SNOOZE"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Time-to-take-your-medicine alerts"; enableVibration(true)
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

    fun dismiss(ctx: Context, doseId: Long) = NotificationManagerCompat.from(ctx).cancel(doseId.toInt())

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, m: Medicine, d: Dose, snoozeMin: Int, h24: Boolean) {
        if (!allowed(ctx)) return
        channels(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val id = d.id
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText("Time to take your medicine!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Time to take your medicine!\nMedicine: ${m.name}\nScheduled time: ${Fmt.time(d.scheduledAt, h24)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF164F7A.toInt())
            .setContentIntent(open)
            .addAction(0, "Taken", pi(ctx, (2_000_000 + id).toInt(), TAKEN, "dose" to id))
            .addAction(0, "Snooze $snoozeMin min", pi(ctx, (4_000_000 + id).toInt(), SNOOZE, "dose" to id))
            .addAction(0, "Skip", pi(ctx, (3_000_000 + id).toInt(), SKIP, "dose" to id))
            .build()
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
