package com.pilltack.app.ui

import com.pilltack.app.data.*
import java.time.LocalDate
import java.time.ZoneId

object Planner {
    /** Stored doses of [d] plus upcoming computed occurrences that have not fired yet. */
    fun slotsFor(d: LocalDate, meds: List<Medicine>, doses: List<Dose>, zone: ZoneId): List<Slot> {
        val from = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val mine = doses.filter { it.scheduledAt in from until to }
        val byId = meds.associateBy { it.id }
        val keys = mine.map { it.medicineId to it.scheduledAt }.toSet()
        val out = mine.map { Slot(byId[it.medicineId], it.scheduledAt, it, it.medName, it.medDetail) }.toMutableList()
        meds.filter { it.status == "ACTIVE" }.forEach { m ->
            ScheduleLogic.occurrences(m, d, zone).filter { (m.id to it) !in keys }.forEach {
                out += Slot(m, it, null, m.name, detailOf(m))
            }
        }
        return out.sortedBy { it.at }
    }

    /** 0 = nothing/pending, 1 = all done, 2 = something missed. */
    fun dayState(d: LocalDate, meds: List<Medicine>, doses: List<Dose>, zone: ZoneId): Int {
        val s = slotsFor(d, meds, doses, zone)
        return when {
            s.isEmpty() -> 0
            s.any { it.status == "MISSED" } -> 2
            s.all { it.status == "TAKEN" || it.status == "SKIPPED" } -> 1
            else -> 0
        }
    }
}

fun detailOf(m: Medicine) = m.form + if (m.strength.isNotBlank()) ", ${m.strength}${m.unit}" else ""

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun freqText(m: Medicine, h24: Boolean): String {
    val base = when (m.scheduleType) {
        "DAILY" -> "Every day"
        "WEEKDAYS" -> dayNames.filterIndexed { i, _ -> ((m.weekdays shr i) and 1) == 1 }.joinToString(", ")
        "DATES" -> "${ScheduleLogic.dates(m).size} selected date(s)"
        else -> "As needed"
    }
    if (m.scheduleType == "AS_NEEDED") return base
    return base + " · " + ScheduleLogic.times(m).joinToString(", ") { Fmt.hm(it.toString(), h24) }
}

fun durationText(m: Medicine): String = when {
    m.scheduleType == "DATES" -> "Ends after the last selected date"
    m.durationType == "DAYS" -> "${m.durationDays} days (until ${Fmt.date(ScheduleLogic.endDate(m)!!)})"
    m.durationType == "END_DATE" -> "Until ${Fmt.date(LocalDate.ofEpochDay(m.endDate))}"
    m.durationType == "DOSES" -> "${m.totalDoses} doses"
    else -> "Ongoing"
}
