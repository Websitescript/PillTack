@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pilltack.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pilltack.app.data.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun HistoryScreen(nav: NavHostController, repo: Repo, medId: Long) {
    val meds by repo.medicines.collectAsState(emptyList())
    val doses by repo.doses.collectAsState(emptyList())
    val prefs by repo.prefs.collectAsState(Prefs())
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var filter by remember { mutableStateOf("Today") }
    var custom by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var picking by remember { mutableStateOf(false) }

    val range: Pair<LocalDate, LocalDate> = when (filter) {
        "Today" -> today to today
        "Yesterday" -> today.minusDays(1) to today.minusDays(1)
        "This week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
        "This month" -> today.withDayOfMonth(1) to today
        else -> custom ?: (today to today)
    }
    val shown = doses.filter {
        val d = Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate()
        d >= range.first && d <= range.second && (medId <= 0 || it.medicineId == medId)
    }
    val byDay = shown.groupBy { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() }.toSortedMap(compareByDescending<LocalDate> { it })
    val medMap = meds.associateBy { it.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            if (medId > 0) BackBar({ nav.popBackStack() }, null) else Box(Modifier.fillMaxWidth()) { Logo() }
            Txt(if (medId > 0) "History: ${medMap[medId]?.name ?: "Medicine"}" else "History", T.h1, modifier = Modifier.padding(vertical = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today", "Yesterday", "This week", "This month", "Custom").forEach { f ->
                    Chip(f, filter == f) { if (f == "Custom") picking = true else filter = f }
                }
            }
            if (filter == "Custom") Txt("${Fmt.date(range.first)} – ${Fmt.date(range.second)}", T.c1, PT.Grey, Modifier.padding(top = 8.dp))
            val t = shown.count { it.status == "TAKEN" }; val s = shown.count { it.status == "SKIPPED" }; val m = shown.count { it.status == "MISSED" }
            Txt("$t taken · $s skipped · $m missed", T.b3, PT.Grey, Modifier.padding(vertical = 12.dp))
            if (shown.isEmpty()) Txt("No history for this period yet.", T.b3, PT.Grey)
        }
        byDay.forEach { (day, list) ->
            item { SectionHeader(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))) }
            items(list.sortedBy { it.scheduledAt }) { d ->
                val med = medMap[d.medicineId]
                val extra = when (d.status) {
                    "TAKEN" -> "Taken at ${Fmt.time(d.actualAt, prefs.h24)}"
                    "SKIPPED" -> "Skipped"
                    "MISSED" -> "Missed"
                    else -> "Waiting for your response"
                } + if (med?.status == "COMPLETED") " · Course completed" else ""
                Box(Modifier.padding(bottom = 8.dp)) { DoseCard(Slot(med, d.scheduledAt, d, d.medName, d.medDetail), prefs.h24, extra) }
            }
        }
    }

    if (picking) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    val a = st.selectedStartDateMillis; val b = st.selectedEndDateMillis
                    if (a != null) { custom = millisToDay(a) to millisToDay(b ?: a); filter = "Custom" }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } }) {
            DateRangePicker(st, Modifier.height(500.dp))
        }
    }
}
