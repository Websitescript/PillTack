package com.pilltack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pilltack.app.data.*
import com.pilltack.app.notify.Reminders
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlarmScreen(repo: Repo, doseId: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by repo.prefs.collectAsState(Prefs())
    val test = doseId == Reminders.TEST_ID
    var dose by remember(doseId) { mutableStateOf<Dose?>(null) }
    var med by remember(doseId) { mutableStateOf<Medicine?>(null) }

    // Close automatically if the dose was handled elsewhere (e.g. from the notification buttons).
    LaunchedEffect(doseId) {
        while (!test) {
            val d = repo.getDose(doseId)
            if (d == null || d.status != "PENDING") { onDone(); break }
            dose = d
            if (med == null) med = repo.getMed(d.medicineId)
            delay(1000)
        }
    }

    fun finishWith(block: suspend () -> Unit) {
        scope.launch { block(); Reminders.dismiss(ctx, doseId); onDone() }
    }

    val name = if (test) "Test medicine" else dose?.medName ?: ""
    val detail = if (test) "This is a test reminder" else dose?.medDetail ?: ""
    val at = if (test) System.currentTimeMillis() else dose?.scheduledAt ?: System.currentTimeMillis()

    Column(Modifier.fillMaxSize().background(PT.Blue).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Txt("PillTrack", T.b1, Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(8.dp))
        Txt(Fmt.time(at, prefs.h24), T.h1.copy(fontSize = androidx.compose.ui.unit.TextUnit(56f, androidx.compose.ui.unit.TextUnitType.Sp)), Color.White)
        Spacer(Modifier.weight(1f))
        if (med != null) MedGlyph(med, 120.dp) else PillGlyph(0, Color(0xFFE5383B), Color.White, Color(0xFFF5A623), 120.dp)
        Spacer(Modifier.height(24.dp))
        Txt("Time to take your medicine!", T.h2, Color.White, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(name, T.h1, Color.White, align = TextAlign.Center, maxLines = 2)
        if (detail.isNotBlank()) Txt(detail, T.b2, Color.White.copy(alpha = 0.85f), align = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(PT.Red, Icons.Filled.Close, "Skip") {
                finishWith { if (!test) repo.mark(doseId, "SKIPPED") }
            }
            RoundAction(Color(0xFF5B6B7A), Icons.Filled.Snooze, "Snooze ${prefs.snooze} min") {
                finishWith { if (!test) repo.snooze(doseId, prefs.snooze) }
            }
            RoundAction(PT.Green, Icons.Filled.Check, "Taken") {
                finishWith { if (!test) repo.mark(doseId, "TAKEN") }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RoundAction(color: Color, icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(8.dp))
        Txt(label, T.c1, Color.White, align = TextAlign.Center)
    }
}
