package com.pilltack.app.data

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure scheduling rules. Everything about "when does this medicine fire" lives here. */
object ScheduleLogic {
    fun times(m: Medicine): List<LocalTime> =
        m.times.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it) }.sorted()

    fun dates(m: Medicine): List<Long> = m.dates.split(",").mapNotNull { it.toLongOrNull() }.sorted()

    /** Last day of treatment, or null when open-ended. Start 5 Nov + 5 days -> 9 Nov. */
    fun endDate(m: Medicine): LocalDate? = when (m.durationType) {
        "DAYS" -> LocalDate.ofEpochDay(m.startDate).plusDays(m.durationDays.toLong() - 1)
        "END_DATE" -> LocalDate.ofEpochDay(m.endDate)
        else -> null
    }

    fun runsOn(m: Medicine, d: LocalDate): Boolean {
        if (m.scheduleType == "AS_NEEDED") return false
        if (d.toEpochDay() < m.startDate) return false
        endDate(m)?.let { if (d.isAfter(it)) return false }
        return when (m.scheduleType) {
            "DAILY" -> true
            "WEEKDAYS" -> ((m.weekdays shr (d.dayOfWeek.value - 1)) and 1) == 1
            "INTERVAL" -> (d.toEpochDay() - m.startDate) % maxOf(m.intervalDays, 1) == 0L
            "DATES" -> dates(m).contains(d.toEpochDay())
            else -> false
        }
    }

    fun occurrences(m: Medicine, d: LocalDate, zone: ZoneId): List<Long> =
        if (!runsOn(m, d)) emptyList()
        else times(m).map { d.atTime(it).atZone(zone).toInstant().toEpochMilli() }.filter { it >= m.activeFrom }

    /** Next dose strictly after [after]; null when the course is over, paused or as-needed. */
    fun next(m: Medicine, after: Long, used: Int, zone: ZoneId): Long? {
        if (m.status != "ACTIVE" || m.scheduleType == "AS_NEEDED") return null
        if (m.durationType == "DOSES" && used >= m.totalDoses) return null
        var d = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        if (d.toEpochDay() < m.startDate) d = LocalDate.ofEpochDay(m.startDate)
        val end = endDate(m)
        repeat(800) {
            if (end != null && d.isAfter(end)) return null
            occurrences(m, d, zone).firstOrNull { it > after }?.let { return it }
            d = d.plusDays(1)
        }
        return null
    }
}

object Fmt {
    private fun p(h24: Boolean) = DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", Locale.getDefault())
    fun time(ms: Long, h24: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        p(h24).format(Instant.ofEpochMilli(ms).atZone(zone))
    fun hm(s: String, h24: Boolean): String = LocalTime.parse(s).format(p(h24))
    fun date(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}
