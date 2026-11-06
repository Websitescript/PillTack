package com.pilltack.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pilltack.app.PillApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles alarm fires, notification buttons, and system events (boot, clock/zone change, app update). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val repo = (ctx.applicationContext as PillApp).repo
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dose = intent.getLongExtra("dose", 0L)
                when (intent.action) {
                    Reminders.FIRE -> {
                        val id = intent.getLongExtra("med", 0L)
                        val at = intent.getLongExtra("at", 0L)
                        val m = repo.getMed(id)
                        if (m != null && m.status == "ACTIVE") {
                            val d = repo.ensureDose(m, at)
                            val p = repo.prefsNow()
                            if (d.status == "PENDING" && p.notif) Reminders.show(ctx, m, d, p.snooze, p.h24)
                        }
                        repo.refresh(id, maxOf(at, System.currentTimeMillis()))
                    }
                    Reminders.SNOOZE_FIRE -> {
                        val d = repo.getDose(dose)
                        val m = d?.let { repo.getMed(it.medicineId) }
                        val p = repo.prefsNow()
                        if (d != null && m != null && d.status == "PENDING" && p.notif) Reminders.show(ctx, m, d, p.snooze, p.h24)
                    }
                    Reminders.TAKEN -> repo.mark(dose, "TAKEN")
                    Reminders.SKIP -> repo.mark(dose, "SKIPPED")
                    Reminders.SNOOZE -> repo.snooze(dose, repo.prefsNow().snooze)
                    else -> repo.sweep()   // boot, time/zone change, app update, exact-alarm permission change
                }
            } finally {
                pending.finish()
            }
        }
    }
}
