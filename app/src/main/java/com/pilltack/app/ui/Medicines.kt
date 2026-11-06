package com.pilltack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pilltack.app.data.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun MedicinesScreen(nav: NavHostController, repo: Repo) {
    val meds by repo.medicines.collectAsState(emptyList())
    val doses by repo.doses.collectAsState(emptyList())
    val prefs by repo.prefs.collectAsState(Prefs())
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<Medicine?>(null) }
    val active = meds.filter { it.status != "COMPLETED" }
    val done = meds.filter { it.status == "COMPLETED" }

    @Composable fun card(m: Medicine) {
        val used = doses.count { it.medicineId == m.id && it.scheduledAt >= m.activeFrom }
        val next = ScheduleLogic.next(m, System.currentTimeMillis(), used, ZoneId.systemDefault())
        val nextText = when {
            m.status == "COMPLETED" -> "Course completed"
            m.status == "PAUSED" -> "Reminders paused"
            m.scheduleType == "AS_NEEDED" -> "No reminders (as needed)"
            next != null -> "Next: " + java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(next), ZoneId.systemDefault())
                .let { java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, ").format(it) } + Fmt.time(next, prefs.h24)
            else -> "No upcoming reminder"
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(PT.Surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedGlyph(m, 56.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(m.name, T.b1); Txt(detailOf(m), T.c1, PT.Grey)
                }
                Txt(m.status.lowercase().replaceFirstChar { it.uppercase() }, T.c2,
                    when (m.status) { "ACTIVE" -> PT.Green; "PAUSED" -> PT.Grey; else -> PT.Link })
            }
            Spacer(Modifier.height(8.dp))
            Txt(freqText(m, prefs.h24), T.c1)
            Txt(durationText(m), T.c1, PT.Grey)
            Txt(nextText, T.c1, PT.Blue)
            FlowRow2 {
                if (m.status != "COMPLETED") TextButton({ nav.navigate("add?med=${m.id}") }) { Txt("Edit", T.c1, PT.Link) }
                TextButton({ nav.navigate("history?med=${m.id}") }) { Txt("History", T.c1, PT.Link) }
                if (m.scheduleType == "AS_NEEDED" && m.status == "ACTIVE") TextButton({ scope.launch { repo.logNow(m) } }) { Txt("Log dose now", T.c1, PT.Link) }
                when (m.status) {
                    "ACTIVE" -> {
                        if (m.scheduleType != "AS_NEEDED") TextButton({ scope.launch { repo.setStatus(m, "PAUSED") } }) { Txt("Pause", T.c1, PT.Link) }
                        TextButton({ scope.launch { repo.setStatus(m, "COMPLETED") } }) { Txt("Stop & archive", T.c1, PT.Red) }
                    }
                    "PAUSED" -> {
                        TextButton({ scope.launch { repo.setStatus(m, "ACTIVE") } }) { Txt("Resume", T.c1, PT.Link) }
                        TextButton({ scope.launch { repo.setStatus(m, "COMPLETED") } }) { Txt("Stop & archive", T.c1, PT.Red) }
                    }
                    else -> TextButton({ scope.launch { repo.reactivate(m) } }) { Txt("Reactivate", T.c1, PT.Link) }
                }
                TextButton({ nav.navigate("refill") }) { Txt("Refill", T.c1, PT.Link) }
                TextButton({ confirmDelete = m }) { Txt("Delete", T.c1, PT.Red) }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            BackBar({ nav.popBackStack() }, null)
            Txt("My Medication", T.h1, modifier = Modifier.padding(vertical = 8.dp))
            PrimaryButton("+  Add medicine") { nav.navigate("add?med=-1") }
            Spacer(Modifier.height(12.dp))
            if (meds.isEmpty()) Txt("No medicines yet.", T.b3, PT.Grey)
        }
        items(active) { card(it) }
        if (done.isNotEmpty()) {
            item { SectionHeader("Completed / archived") }
            items(done) { card(it) }
        }
    }

    confirmDelete?.let { m ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, containerColor = androidx.compose.ui.graphics.Color.White,
            title = { Txt("Delete ${m.name}?", T.h2) },
            text = { Txt("This removes the medicine and its reminders. Past history entries stay in History. Consider \u201CStop & archive\u201D instead.", T.b3) },
            confirmButton = { TextButton({ scope.launch { repo.delete(m) }; confirmDelete = null }) { Text("Delete", color = PT.Red) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Keep") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) = FlowRow(Modifier.fillMaxWidth()) { content() }
