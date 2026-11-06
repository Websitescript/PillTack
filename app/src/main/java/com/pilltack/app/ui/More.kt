@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pilltack.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.pilltack.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RefillScreen(nav: NavHostController, repo: Repo) {
    val meds by repo.medicines.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<Medicine?>(null) }
    var open by remember { mutableStateOf(false) }
    var stock by remember { mutableStateOf("") }
    var alertAt by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar({ nav.popBackStack() }, { nav.popBackStack() })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("Refill Reminder", T.h1, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            Box {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(PT.Surface)
                    .clickable { open = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(chosen?.name ?: "Select Medicine", T.b3, if (chosen == null) PT.Grey else PT.Ink, Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, null)
                }
                DropdownMenu(open, { open = false }) {
                    meds.forEach { m -> DropdownMenuItem({ Text(m.name) }, {
                        chosen = m; open = false
                        stock = if (m.pillsLeft >= 0) m.pillsLeft.toString() else ""
                        alertAt = if (m.refillAt >= 0) m.refillAt.toString() else ""
                    }) }
                }
            }
            Txt("Medicines left?", T.c1)
            Field(stock, { stock = it.filter(Char::isDigit) }, "Number of pills / doses left", number = true)
            Txt("How many left do you want to be alerted at?", T.c1)
            Field(alertAt, { alertAt = it.filter(Char::isDigit) }, "e.g. 5", number = true)
            Txt("Each dose marked Taken reduces the count by the dose amount, and you get a refill notification at or below your limit.", T.c2, PT.Grey)
            PrimaryButton("Set Refill Reminder", enabled = chosen != null) {
                chosen?.let { m -> scope.launch {
                    repo.save(m.copy(pillsLeft = stock.toIntOrNull() ?: -1, refillAt = alertAt.toIntOrNull() ?: -1, activeFrom = m.activeFrom))
                    nav.popBackStack()
                } }
            }
        }
    }
}

@Composable
fun MenuScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        BackBar({ nav.popBackStack() }, null)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("My Medication", Icons.Filled.Medication, "medicines"),
                Triple("Refill Reminder", Icons.Filled.Inventory2, "refill"),
                Triple("Notifications & Settings", Icons.Filled.Settings, "settings")
            ).forEach { (t, ic, r) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(PT.Surface)
                    .clickable { nav.navigate(r) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, null, tint = PT.Blue); Spacer(Modifier.width(12.dp)); Txt(t, T.b2, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = PT.Grey)
                }
            }
        }
    }
}

@Composable
fun SuccessScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { delay(6000); nav.navigate("home") { popUpTo("success") { inclusive = true } } }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            IconButton({ nav.navigate("home") { popUpTo("success") { inclusive = true } } }) { Icon(Icons.Filled.Close, "Close") }
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.CheckCircle, null, tint = PT.Green, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Txt("Reminder added successfully!", T.h2, align = TextAlign.Center)
        Txt("Stay on track for a healthier you.", T.b3, PT.Grey, align = TextAlign.Center)
        if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(PT.Surface).padding(14.dp)) {
                Txt("For on-time reminders, allow exact alarms. Without it Android may delay notifications.", T.c1)
                TextButton({ ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }) {
                    Txt("Allow exact alarms", T.b2, PT.Link)
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SettingsScreen(nav: NavHostController, repo: Repo) {
    val ctx = LocalContext.current
    val prefs by repo.prefs.collectAsState(Prefs())
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    // Re-check system permission state whenever the user returns from system settings.
    DisposableEffect(Unit) {
        val act = ctx as ComponentActivity
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        act.lifecycle.addObserver(ob); onDispose { act.lifecycle.removeObserver(ob) }
    }
    val sysNotif = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exact = remember(tick) { Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val battery = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }

    @Composable fun status(title: String, ok: Boolean, why: String, button: String, onClick: () -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PT.Surface).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (ok) PT.Green else PT.Red)
                Spacer(Modifier.width(8.dp)); Txt(title, T.b2)
            }
            Txt(why, T.c1, PT.Grey, Modifier.padding(vertical = 6.dp))
            if (!ok) TextButton(onClick) { Txt(button, T.b2, PT.Link) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar({ nav.popBackStack() }, null)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("Settings", T.h1)
            Txt("Reminder reliability", T.b1)
            status("Notifications", sysNotif,
                if (sysNotif) "Android is allowed to show PillTack reminders." else "Reminders are blocked by Android. You will not see or hear any alerts until you allow notifications.",
                "Open notification settings") {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            status("Exact alarms", exact,
                if (exact) "Reminders can fire at the exact minute." else "Without this, Android may delay reminders by several minutes.",
                "Allow exact alarms") {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
            status("Battery optimisation", battery,
                if (battery) "PillTack is not restricted in the background." else "Battery saving can delay or block alarms. Set PillTack to Unrestricted / Don't optimise.",
                "Open battery settings") { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            Txt("On some phones (Realme/Oppo, Xiaomi, Vivo, Samsung) also enable Auto-launch / Allow background activity for PillTack in the phone's app settings, and don't swipe it out of a \u201Cclean up\u201D list. Android and phone makers can still delay alarms, so reminders can't be guaranteed.",
                T.c2, PT.Grey)

            Txt("Preferences", T.b1, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Show reminder notifications", T.b3, modifier = Modifier.weight(1f))
                Switch(prefs.notif, { scope.launch { repo.setNotif(it) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = PT.Green, checkedThumbColor = Color.White))
            }
            Txt("Default snooze", T.b3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { m -> Chip("$m min", prefs.snooze == m) { scope.launch { repo.setSnooze(m) } } }
            }
            Txt("Time format", T.b3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("12-hour", !prefs.h24) { scope.launch { repo.setH24(false) } }
                Chip("24-hour", prefs.h24) { scope.launch { repo.setH24(true) } }
            }

            Txt("Data", T.b1, modifier = Modifier.padding(top = 8.dp))
            SoftButton("Export history (CSV)") {
                scope.launch {
                    val csv = repo.csv()
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "PillTack history").putExtra(Intent.EXTRA_TEXT, csv), "Export history"))
                }
            }
            SoftButton("Clear medication history") { confirmClear = true }
            SoftButton("About PillTack") { about = true }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, containerColor = Color.White,
        title = { Txt("Clear all history?", T.h2) },
        text = { Txt("Every taken, skipped and missed entry will be permanently deleted. Your medicines stay.", T.b3) },
        confirmButton = { TextButton({ scope.launch { repo.clearHistory() }; confirmClear = false }) { Text("Clear", color = PT.Red) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Keep") } })
    if (about) AlertDialog(onDismissRequest = { about = false }, containerColor = Color.White,
        title = { Txt("PillTack", T.h2) },
        text = { Txt("Medication reminders that work offline. Your data stays on this phone.\nVersion 1.0", T.b3) },
        confirmButton = { TextButton({ about = false }) { Text("OK") } })
}
