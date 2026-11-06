package com.pilltack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pilltack.app.data.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun HomeScreen(nav: NavHostController, repo: Repo) {
    val meds by repo.medicines.collectAsState(emptyList())
    val doses by repo.doses.collectAsState(emptyList())
    val prefs by repo.prefs.collectAsState(Prefs())
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var sel by remember { mutableStateOf(today) }
    var dialog by remember { mutableStateOf<Slot?>(null) }

    val slots = remember(meds, doses, sel) { Planner.slotsFor(sel, meds, doses, zone) }
    val total = slots.size
    val taken = slots.count { it.status == "TAKEN" }
    val missed = slots.count { it.status == "MISSED" }
    val remaining = slots.count { it.open }
    val upcoming = slots.filter { it.open }
    val logged = slots.filter { !it.open }
    val weekStart = sel.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val title = (if (sel == today) "Today, " else sel.format(DateTimeFormatter.ofPattern("EEE, ", Locale.getDefault()))) +
        sel.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ nav.navigate("menu") }) { Icon(Icons.Filled.Menu, "Menu") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Logo() }
                IconButton({ nav.navigate("settings") }) { Icon(Icons.Filled.Notifications, "Notification settings") }
            }
            Txt(title, T.h2, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), align = TextAlign.Center)
            Row(Modifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val d = weekStart.plusDays(i.toLong())
                    val st = Planner.dayState(d, meds, doses, zone)
                    Column(Modifier.weight(1f).clickable { sel = d }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), T.c2,
                            if (d == sel) PT.Blue else PT.Grey)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape)
                            .background(if (st == 0) PT.Surface else Color.Transparent), contentAlignment = Alignment.Center) {
                            when (st) {
                                1 -> Icon(Icons.Filled.CheckCircle, "All taken", tint = PT.Green)
                                2 -> Icon(Icons.Filled.Cancel, "Missed", tint = PT.Red)
                                else -> if (d == sel) Box(Modifier.size(10.dp).clip(CircleShape).background(PT.Blue))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Daily progress summary
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PT.Blue).padding(16.dp)) {
                Txt(if (sel == today) "Today's Medication" else "Medication", T.b1, Color.White)
                Spacer(Modifier.height(4.dp))
                Txt("$total scheduled · $taken taken · $remaining remaining" + if (missed > 0) " · $missed missed" else "",
                    T.c1, Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { if (total == 0) 0f else taken.toFloat() / total },
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.3f))
                Spacer(Modifier.height(10.dp))
                val next = upcoming.firstOrNull()
                Txt(when {
                    total == 0 -> "Nothing scheduled for this day"
                    next != null -> "Next: ${next.name} at ${Fmt.time(next.at, prefs.h24)}"
                    else -> "All doses for this day are done"
                }, T.c1, Color.White)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("+  Add medicine") { nav.navigate("add?med=-1") }
            if (upcoming.isNotEmpty()) SectionHeader("Upcoming")
        }
        items(upcoming) { s -> Box(Modifier.padding(bottom = 8.dp)) { DoseCard(s, prefs.h24) { dialog = s } } }
        item { if (logged.isNotEmpty()) SectionHeader("Logged Medication") }
        items(logged) { s ->
            Box(Modifier.padding(bottom = 8.dp)) {
                DoseCard(s, prefs.h24, if (s.status == "TAKEN" && s.dose != null && s.dose.actualAt > 0)
                    "Taken at ${Fmt.time(s.dose.actualAt, prefs.h24)}" else s.status.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
        item {
            val active = meds.filter { it.status != "COMPLETED" }
            SectionHeader("My Medication", "Edit") { nav.navigate("medicines") }
            if (active.isEmpty()) Txt("No medicines yet. Tap Add medicine to start.", T.c1, PT.Grey)
        }
        items(meds.filter { it.status != "COMPLETED" }.take(4)) { m ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(PT.Surface)
                .clickable { nav.navigate("add?med=${m.id}") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MedGlyph(m)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row { Txt("Medicine Name: ", T.c1, PT.Grey); Txt(m.name, T.c1) }
                    Txt(detailOf(m), T.c1, PT.Grey)
                    Txt(freqText(m, prefs.h24) + if (m.status == "PAUSED") " (paused)" else "", T.c2, PT.Grey)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = PT.Grey)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    dialog?.let { s ->
        DoseDialog(s, prefs.h24,
            onTaken = { dialog = null; scope.launch { s.med?.let { m -> repo.markOccurrence(m, s.at, "TAKEN") } } },
            onSkip = { dialog = null; scope.launch { s.med?.let { m -> repo.markOccurrence(m, s.at, "SKIPPED") } } },
            onSnooze = { min -> dialog = null; scope.launch { s.dose?.let { d -> repo.snooze(d.id, min) } } },
            onDismiss = { dialog = null })
    }
}
