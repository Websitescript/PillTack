package com.pilltack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pilltack.app.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val Purple = Color(0xFF8E5BD9)

@Composable
fun CalendarScreen(repo: Repo) {
    val meds by repo.medicines.collectAsState(emptyList())
    val doses by repo.doses.collectAsState(emptyList())
    val prefs by repo.prefs.collectAsState(Prefs())
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var sel by remember { mutableStateOf(LocalDate.now()) }
    var dialog by remember { mutableStateOf<Slot?>(null) }
    val slots = remember(meds, doses, sel) { Planner.slotsFor(sel, meds, doses, zone) }
    val endsToday = meds.filter { ScheduleLogic.endDate(it) == sel || (it.scheduleType == "DATES" && ScheduleLogic.dates(it).lastOrNull() == sel.toEpochDay()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Logo() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
                Txt("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", T.h2,
                    modifier = Modifier.weight(1f), align = TextAlign.Center)
                IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "Next month") }
            }
            Row { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Txt(it, T.c2, PT.Grey, Modifier.weight(1f), TextAlign.Center) } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val len = month.lengthOfMonth()
            for (r in 0 until (offset + len + 6) / 7) Row {
                for (c in 0..6) {
                    val day = r * 7 + c - offset + 1
                    if (day !in 1..len) { Spacer(Modifier.weight(1f)); continue }
                    val d = month.atDay(day)
                    val s = remember(meds, doses, d) { Planner.slotsFor(d, meds, doses, zone) }
                    val ends = meds.any { ScheduleLogic.endDate(it) == d }
                    Column(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (d == sel) PT.Blue else Color.Transparent)
                        .then(if (d == LocalDate.now() && d != sel) Modifier.border(1.dp, PT.Blue, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { sel = d }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("$day", T.c1, if (d == sel) Color.White else PT.Ink)
                        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (s.any { it.open }) Dot(PT.Link)
                            if (s.any { it.status == "TAKEN" }) Dot(PT.Green)
                            if (s.any { it.status == "MISSED" }) Dot(PT.Red)
                            if (s.any { it.status == "SKIPPED" }) Dot(PT.Grey)
                            if (ends) Dot(Purple)
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Legend("Scheduled", PT.Link); Legend("Taken", PT.Green); Legend("Missed", PT.Red); Legend("Skipped", PT.Grey); Legend("Ends", Purple)
            }
            SectionHeader(if (sel == LocalDate.now()) "Today" else Fmt.date(sel))
            if (slots.isEmpty() && endsToday.isEmpty()) Txt("No medication on this day.", T.b3, PT.Grey)
            endsToday.forEach { Txt("Treatment ends: ${it.name}", T.b2, Purple, Modifier.padding(bottom = 8.dp)) }
        }
        items(slots) { s -> Box(Modifier.padding(bottom = 8.dp)) { DoseCard(s, prefs.h24, if (s.open) null else s.status.lowercase().replaceFirstChar { it.uppercase() }) { if (s.open) dialog = s } } }
    }
    dialog?.let { s ->
        DoseDialog(s, prefs.h24,
            onTaken = { dialog = null; scope.launch { s.med?.let { repo.markOccurrence(it, s.at, "TAKEN") } } },
            onSkip = { dialog = null; scope.launch { s.med?.let { repo.markOccurrence(it, s.at, "SKIPPED") } } },
            onSnooze = { min -> dialog = null; scope.launch { s.dose?.let { repo.snooze(it.id, min) } } },
            onDismiss = { dialog = null })
    }
}

@Composable private fun Dot(c: Color) = Box(Modifier.size(6.dp).clip(CircleShape).background(c))

@Composable private fun Legend(t: String, c: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Dot(c); Spacer(Modifier.width(4.dp)); Txt(t, T.c2, PT.Grey)
}
