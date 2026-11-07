package com.pilltack.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.pilltack.app.notify.Reminders
import com.pilltack.app.widget.NextDoseWidget
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

val Context.store by preferencesDataStore("settings")

data class Prefs(val snooze: Int = 10, val h24: Boolean = false, val notif: Boolean = true, val onboarded: Boolean = false, val alarm: Boolean = true)

class Repo(private val ctx: Context, db: AppDb) {
    private val md = db.meds()
    private val dd = db.doses()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    companion object {
        val SNOOZE = intPreferencesKey("snooze")
        val H24 = booleanPreferencesKey("h24")
        val NOTIF = booleanPreferencesKey("notif")
        val ONB = booleanPreferencesKey("onboarded")
        val ALARM = booleanPreferencesKey("alarm")
        const val GRACE = 60 * 60_000L   // a dose not acted on for 1h becomes MISSED
    }

    val medicines: Flow<List<Medicine>> = md.all()
    val doses: Flow<List<Dose>> = dd.allFlow()
    val prefs: Flow<Prefs> = ctx.store.data.map {
        Prefs(it[SNOOZE] ?: 10, it[H24] ?: false, it[NOTIF] ?: true, it[ONB] ?: false, it[ALARM] ?: true)
    }

    suspend fun prefsNow(): Prefs = prefs.first()
    suspend fun setSnooze(v: Int) { ctx.store.edit { it[SNOOZE] = v } }
    suspend fun setH24(v: Boolean) { ctx.store.edit { it[H24] = v }; updateWidget() }
    suspend fun setAlarm(v: Boolean) { ctx.store.edit { it[ALARM] = v } }
    suspend fun setNotif(v: Boolean) { ctx.store.edit { it[NOTIF] = v } }
    suspend fun setOnboarded() { ctx.store.edit { it[ONB] = true } }

    suspend fun getMed(id: Long): Medicine? = md.get(id)

    fun detail(m: Medicine): String =
        m.form + if (m.strength.isNotBlank()) ", ${m.strength}${m.unit}" else ""

    /** Insert or update; always cancels the old alarm and schedules a fresh one. */
    suspend fun save(m: Medicine): Long {
        val id = if (m.id == 0L) md.insert(m) else { md.update(m); m.id }
        refresh(id)
        return id
    }

    suspend fun delete(m: Medicine) { Reminders.cancel(ctx, m.id); md.delete(m); updateWidget() }

    suspend fun setStatus(m: Medicine, status: String) {
        md.update(m.copy(status = status)); refresh(m.id)
    }

    /** Restart a finished/stopped course from today with the same settings. */
    suspend fun reactivate(m: Medicine) {
        md.update(m.copy(
            status = "ACTIVE",
            startDate = LocalDate.now(zone).toEpochDay(),
            activeFrom = System.currentTimeMillis(),
            durationType = if (m.durationType == "END_DATE") "ONGOING" else m.durationType,
            scheduleType = if (m.scheduleType == "DATES") "DAILY" else m.scheduleType
        ))
        refresh(m.id)
    }

    suspend fun ensureDose(m: Medicine, at: Long, status: String = "PENDING"): Dose {
        dd.find(m.id, at)?.let { return it }
        dd.insert(Dose(medicineId = m.id, scheduledAt = at, status = status, medName = m.name, medDetail = detail(m)))
        return dd.find(m.id, at)!!
    }

    suspend fun getDose(id: Long): Dose? = dd.get(id)

    /** Mark one dose. Never deletes the medicine; refresh() decides whether the course has ended. */
    suspend fun mark(doseId: Long, status: String) {
        val d = dd.get(doseId) ?: return
        dd.update(d.copy(status = status, actualAt = if (status == "TAKEN") System.currentTimeMillis() else 0))
        Reminders.dismiss(ctx, doseId)
        Reminders.cancelSnooze(ctx, doseId)
        val m = md.get(d.medicineId) ?: return
        if (status == "TAKEN" && m.pillsLeft >= 0) {
            val left = (m.pillsLeft - m.doseAmount.coerceAtLeast(1)).coerceAtLeast(0)
            md.update(m.copy(pillsLeft = left))
            if (m.refillAt >= 0 && left <= m.refillAt) Reminders.refillNotice(ctx, m.copy(pillsLeft = left))
        }
        refresh(m.id)
        updateWidget()
    }

    suspend fun markOccurrence(m: Medicine, at: Long, status: String) {
        mark(ensureDose(m, at).id, status)
    }

    /** As-needed medicines: log a dose right now. */
    suspend fun logNow(m: Medicine) {
        val at = System.currentTimeMillis()
        val d = ensureDose(m, at)
        mark(d.id, "TAKEN")
    }

    suspend fun snooze(doseId: Long, minutes: Int) {
        dd.get(doseId) ?: return
        Reminders.dismiss(ctx, doseId)
        Reminders.setSnooze(ctx, doseId, System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Compute the next dose and set ONE alarm for this medicine (same request code => no duplicates). */
    suspend fun refresh(id: Long, after: Long = System.currentTimeMillis()) {
        refreshAlarm(id, after)
        updateWidget()
    }

    private suspend fun refreshAlarm(id: Long, after: Long) {
        val m = md.get(id)
        if (m == null || m.status != "ACTIVE") { Reminders.cancel(ctx, id); return }
        val nxt = ScheduleLogic.next(m, after, dd.countFor(id, m.activeFrom), zone)
        if (nxt != null) {
            Reminders.setMedAlarm(ctx, id, nxt)
        } else {
            Reminders.cancel(ctx, id)
            if (m.scheduleType != "AS_NEEDED" && dd.pendingFor(id) == 0) {
                md.update(m.copy(status = "COMPLETED"))   // archived; history is untouched
            }
        }
    }

    suspend fun rescheduleAll() { md.allNow().forEach { refresh(it.id) } }

    /** Mark stale doses MISSED, record doses that never fired (phone off), then re-arm all alarms. */
    suspend fun sweep() {
        val now = System.currentTimeMillis()
        dd.stalePending(now - GRACE).forEach { dd.update(it.copy(status = "MISSED")) }
        val today = LocalDate.now(zone)
        md.allNow().filter { it.status == "ACTIVE" }.forEach { m ->
            for (back in 0L..3L) {
                ScheduleLogic.occurrences(m, today.minusDays(back), zone).filter { it < now - GRACE }.forEach { at ->
                    if (dd.find(m.id, at) == null)
                        dd.insert(Dose(medicineId = m.id, scheduledAt = at, status = "MISSED", medName = m.name, medDetail = detail(m)))
                }
            }
        }
        rescheduleAll()
    }

    suspend fun clearHistory() { dd.clear() }

    suspend fun csv(): String {
        val f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder("Medicine,Details,Scheduled,Status,Taken at\n")
        dd.allNow().forEach {
            sb.append("\"${it.medName}\",\"${it.medDetail}\",")
                .append(f.format(java.time.Instant.ofEpochMilli(it.scheduledAt).atZone(zone))).append(',')
                .append(it.status).append(',')
                .append(if (it.actualAt > 0) f.format(java.time.Instant.ofEpochMilli(it.actualAt).atZone(zone)) else "").append('\n')
        }
        return sb.toString()
    }

    /** Earliest upcoming dose across all active medicines. */
    suspend fun nextOverall(): Pair<Medicine, Long>? {
        val now = System.currentTimeMillis()
        return md.allNow().mapNotNull { m ->
            ScheduleLogic.next(m, now, dd.countFor(m.id, m.activeFrom), zone)?.let { m to it }
        }.minByOrNull { it.second }
    }

    /** Redraw every placed home-screen widget with the next dose (or "Due now" if one is waiting). */
    suspend fun updateWidget() {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, NextDoseWidget::class.java))
        if (ids.isEmpty()) return
        val h24 = prefsNow().h24
        val pending = dd.firstPending()
        val nxt = nextOverall()
        val dayFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
        val (name, whenText) = when {
            pending != null -> pending.medName to "Due now \u00B7 " + Fmt.time(pending.scheduledAt, h24)
            nxt != null -> nxt.first.name to
                dayFmt.format(java.time.Instant.ofEpochMilli(nxt.second).atZone(zone)) + ", " + Fmt.time(nxt.second, h24)
            else -> "No upcoming dose" to "Tap to add a medicine"
        }
        ids.forEach { mgr.updateAppWidget(it, NextDoseWidget.views(ctx, name, whenText)) }
    }
}
