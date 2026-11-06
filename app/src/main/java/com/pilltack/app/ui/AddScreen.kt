@file:OptIn(ExperimentalLayoutApi::class)

package com.pilltack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pilltack.app.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Editable form state for the add/edit wizard. */
class Draft {
    var id by mutableLongStateOf(0L)
    var name by mutableStateOf("")
    var form by mutableStateOf("Tablet")
    var customForm by mutableStateOf("")
    var strength by mutableStateOf("")
    var unit by mutableStateOf("mg")
    var shape by mutableIntStateOf(0)
    var cL by mutableIntStateOf(0xFFE5383B.toInt())
    var cR by mutableIntStateOf(0xFFFFFFFF.toInt())
    var cB by mutableIntStateOf(0xFFF5A623.toInt())
    var sched by mutableStateOf("DAILY")
    var weekdays by mutableIntStateOf(0)
    val dates = mutableStateListOf<Long>()
    val times = mutableStateListOf("08:00")
    var dose by mutableIntStateOf(1)
    var start by mutableLongStateOf(LocalDate.now().toEpochDay())
    var durType by mutableStateOf("ONGOING")
    var days by mutableStateOf("7")
    var end by mutableLongStateOf(LocalDate.now().plusDays(6).toEpochDay())
    var doses by mutableStateOf("10")
    var notes by mutableStateOf("")
    var stock by mutableStateOf("")
    var refillAt by mutableStateOf("")
    var status = "ACTIVE"

    fun toMedicine(): Medicine = Medicine(
        id = id, name = name.trim(),
        form = if (form == "Other" && customForm.isNotBlank()) customForm.trim() else form,
        strength = strength.trim(), unit = unit, shape = shape, colorLeft = cL, colorRight = cR, colorBg = cB,
        notes = notes.trim(), scheduleType = sched, weekdays = weekdays,
        dates = dates.sorted().joinToString(","), times = times.distinct().sorted().joinToString(","),
        doseAmount = dose,
        startDate = if (sched == "DATES") (dates.minOrNull() ?: start) else start,
        durationType = if (sched == "DATES" || sched == "AS_NEEDED") "ONGOING" else durType,
        durationDays = days.toIntOrNull() ?: 1, endDate = end, totalDoses = doses.toIntOrNull() ?: 1,
        status = status, pillsLeft = stock.toIntOrNull() ?: -1, refillAt = refillAt.toIntOrNull() ?: -1,
        activeFrom = System.currentTimeMillis()
    )

    fun validate(): Pair<Int, String>? = when {
        name.isBlank() -> 0 to "Enter the medicine name"
        sched == "WEEKDAYS" && weekdays == 0 -> 5 to "Select at least one weekday"
        sched == "DATES" && dates.isEmpty() -> 5 to "Select at least one date"
        sched != "AS_NEEDED" && times.isEmpty() -> 6 to "Add at least one reminder time"
        (sched == "DAILY" || sched == "WEEKDAYS") && durType == "DAYS" && (days.toIntOrNull() ?: 0) < 1 -> 7 to "Enter the number of days"
        (sched == "DAILY" || sched == "WEEKDAYS") && durType == "DOSES" && (doses.toIntOrNull() ?: 0) < 1 -> 7 to "Enter the number of doses"
        (sched == "DAILY" || sched == "WEEKDAYS") && durType == "END_DATE" && end < start -> 7 to "End date must not be before the start date"
        else -> null
    }

    companion object {
        fun from(m: Medicine) = Draft().apply {
            id = m.id; name = m.name; strength = m.strength; unit = m.unit; shape = m.shape
            val common = listOf("Capsule", "Tablet", "Liquid", "Topical", "Cream", "Drops", "Inhaler", "Injection", "Syrup")
            if (m.form in common) form = m.form else { form = "Other"; customForm = m.form }
            cL = m.colorLeft; cR = m.colorRight; cB = m.colorBg
            sched = m.scheduleType; weekdays = m.weekdays
            dates.clear(); dates.addAll(ScheduleLogic.dates(m))
            times.clear(); times.addAll(m.times.split(",").filter { it.isNotBlank() })
            dose = m.doseAmount; start = m.startDate; durType = m.durationType
            days = m.durationDays.toString(); end = if (m.endDate > 0) m.endDate else LocalDate.now().plusDays(6).toEpochDay()
            doses = m.totalDoses.toString(); notes = m.notes
            stock = if (m.pillsLeft >= 0) m.pillsLeft.toString() else ""
            refillAt = if (m.refillAt >= 0) m.refillAt.toString() else ""
            status = m.status
        }
    }
}

@Composable
fun AddScreen(nav: NavHostController, repo: Repo, medId: Long) {
    val scope = rememberCoroutineScope()
    val prefs by repo.prefs.collectAsState(Prefs())
    var draft by remember(medId) { mutableStateOf<Draft?>(if (medId <= 0) Draft() else null) }
    var step by remember(medId) { mutableIntStateOf(if (medId > 0) 8 else 0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(medId) { if (medId > 0) repo.getMed(medId)?.let { draft = Draft.from(it) } }
    val d = draft ?: return
    val editing = d.id > 0

    fun next() { error = null; step = if (step == 5 && d.sched == "AS_NEEDED") 8 else if (step == 6 && d.sched == "DATES") 8 else step + 1 }
    fun back() {
        error = null
        if (step == 0 || (editing && step == 8)) nav.popBackStack()
        else step = if (step == 8 && d.sched == "AS_NEEDED") 5 else step - 1
    }
    fun save() {
        val err = d.validate()
        if (err != null) { step = err.first; error = err.second; return }
        scope.launch {
            repo.save(d.toMedicine())
            if (editing) nav.popBackStack()
            else nav.navigate("success") { popUpTo("home") }
        }
    }

    val titles = listOf("Medication Name", "Choose the Medication Type", "Add the Medication Strength", "Choose Shape",
        "Choose Colours", "How often?", "When will you take this?", "How long?", "Review Details")

    Column(Modifier.fillMaxSize()) {
        BackBar({ back() }, { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (step in 1..7) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    PillGlyph(d.shape, Color(d.cL), Color(d.cR), Color(d.cB), 72.dp)
                }
            }
            Txt(titles[step], T.h1.copy(fontSize = 24.sp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), align = TextAlign.Center)
            error?.let { Txt(it, T.c1, PT.Red, Modifier.padding(bottom = 8.dp)) }
            when (step) {
                0 -> StepName(d)
                1 -> StepType(d)
                2 -> StepStrength(d)
                3 -> StepShape(d)
                4 -> StepColours(d)
                5 -> StepSchedule(d)
                6 -> StepTimes(d, prefs.h24)
                7 -> StepDuration(d)
                else -> StepReview(d, prefs.h24) { step = it }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step == 8) PrimaryButton(if (editing) "Save changes" else "Set Reminder") { save() }
            else PrimaryButton("Next", enabled = step != 0 || d.name.isNotBlank()) { next() }
            if (step in 2..4) SoftButton("Skip this step") { next() }
        }
    }
}

@Composable private fun StepName(d: Draft) {
    Txt("Enter the medicine name as written on the prescription or pack.", T.b3, PT.Grey, Modifier.padding(bottom = 12.dp))
    Field(d.name, { d.name = it }, "Medicine name")
}

private val commonForms = listOf("Capsule", "Tablet", "Liquid", "Topical")
private val moreForms = listOf("Cream", "Drops", "Inhaler", "Injection", "Syrup", "Other")

@Composable private fun StepType(d: Draft) {
    Txt("Common Forms", T.b2, modifier = Modifier.padding(bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { commonForms.forEach { f -> OptionRow(f, d.form == f) { d.form = f } } }
    Txt("More Forms", T.b2, modifier = Modifier.padding(vertical = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { moreForms.forEach { f -> OptionRow(f, d.form == f) { d.form = f } } }
    if (d.form == "Other") { Spacer(Modifier.height(8.dp)); Field(d.customForm, { d.customForm = it }, "Custom type, e.g. Patch") }
}

@Composable private fun StepStrength(d: Draft) {
    Txt("Strength", T.b2, modifier = Modifier.padding(bottom = 8.dp))
    Field(d.strength, { d.strength = it }, "Add Strength", number = true)
    Txt("Choose Unit", T.b2, modifier = Modifier.padding(vertical = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("mg", "mcg", "g", "ml", "%").forEach { u -> Chip(u, d.unit == u) { d.unit = u } } }
}

@Composable private fun StepShape(d: Draft) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        shapeNames.forEachIndexed { i, n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { d.shape = i }) {
                PillGlyph(i, Color(d.cL), Color(d.cR), if (d.shape == i) Color(0xFFD6E4F2) else PT.Surface, 70.dp)
                Txt(n, T.c2, if (d.shape == i) PT.Blue else PT.Grey)
            }
        }
    }
}

@Composable private fun Swatches(title: String, current: Int, onPick: (Int) -> Unit) {
    Txt(title, T.b2, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        palette.forEach { c ->
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                .then(if (c == current) Modifier.border(3.dp, PT.Blue, CircleShape) else Modifier).clickable { onPick(c) })
        }
    }
}

@Composable private fun StepColours(d: Draft) {
    Swatches("Left Side", d.cL) { d.cL = it }
    Swatches("Right Side", d.cR) { d.cR = it }
    Swatches("Background", d.cB) { d.cB = it }
}

private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable private fun StepSchedule(d: Draft) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionRow("Every day", d.sched == "DAILY") { d.sched = "DAILY" }
        OptionRow("On specific days of the week", d.sched == "WEEKDAYS") { d.sched = "WEEKDAYS" }
        OptionRow("On specific dates", d.sched == "DATES") { d.sched = "DATES" }
        OptionRow("As needed (no reminders)", d.sched == "AS_NEEDED") { d.sched = "AS_NEEDED" }
    }
    Spacer(Modifier.height(16.dp))
    when (d.sched) {
        "WEEKDAYS" -> {
            Txt("Choose days", T.b2, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLabels.forEachIndexed { i, l ->
                    val on = ((d.weekdays shr i) and 1) == 1
                    Chip(l, on) { d.weekdays = d.weekdays xor (1 shl i) }
                }
            }
        }
        "DATES" -> MultiDates(d.dates)
    }
    if (d.sched == "DAILY" || d.sched == "WEEKDAYS") { Spacer(Modifier.height(16.dp)); DateRow("Start date", d.start) { d.start = it } }
}

@Composable private fun StepTimes(d: Draft, h24: Boolean) {
    var editing by remember { mutableIntStateOf(-1) }
    Txt("Quick pick", T.b2, modifier = Modifier.padding(bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Once a day" to listOf("08:00"), "Twice a day" to listOf("08:00", "20:00"),
            "3 times a day" to listOf("08:00", "14:00", "20:00"), "4 times a day" to listOf("08:00", "12:00", "16:00", "20:00"))
            .forEach { (l, t) -> Chip(l, d.times.toList() == t) { d.times.clear(); d.times.addAll(t) } }
    }
    Txt("Time of the day", T.b2, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.times.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(PT.Surface)
                .clickable { editing = i }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(Fmt.hm(t, h24), T.b1, modifier = Modifier.weight(1f))
                Txt("${d.dose} ${d.form}", T.c1, PT.Grey)
                IconButton({ d.times.removeAt(i) }) { Icon(Icons.Filled.RemoveCircleOutline, "Remove time", tint = PT.Red) }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(PT.Surface)
            .clickable { d.times.add("09:00"); editing = d.times.size - 1 }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AddCircleOutline, null, tint = PT.Green); Spacer(Modifier.width(8.dp)); Txt("Add a time", T.b2)
        }
    }
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("Dose per reminder", T.b2, modifier = Modifier.weight(1f))
        IconButton({ if (d.dose > 1) d.dose-- }) { Icon(Icons.Filled.Remove, "Fewer") }
        Txt("${d.dose}", T.b1)
        IconButton({ d.dose++ }) { Icon(Icons.Filled.Add, "More") }
    }
    if (editing in d.times.indices) TimeDialog(d.times[editing], h24, { d.times[editing] = it; editing = -1 }, { editing = -1 })
}

@Composable private fun StepDuration(d: Draft) {
    if (d.sched == "DATES") {
        Txt("Reminders only happen on the dates you picked and stop after the last one.", T.b3, PT.Grey); return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionRow("Ongoing (until I stop it)", d.durType == "ONGOING") { d.durType = "ONGOING" }
        OptionRow("For a number of days", d.durType == "DAYS") { d.durType = "DAYS" }
        OptionRow("Until a specific date", d.durType == "END_DATE") { d.durType = "END_DATE" }
        OptionRow("For a number of doses", d.durType == "DOSES") { d.durType = "DOSES" }
    }
    Spacer(Modifier.height(16.dp))
    when (d.durType) {
        "DAYS" -> {
            Field(d.days, { d.days = it.filter(Char::isDigit) }, "Number of days", number = true)
            val n = d.days.toIntOrNull() ?: 0
            if (n > 0) Txt("Starts ${Fmt.date(LocalDate.ofEpochDay(d.start))}, last day ${Fmt.date(LocalDate.ofEpochDay(d.start + n - 1))}",
                T.c1, PT.Grey, Modifier.padding(top = 8.dp))
        }
        "END_DATE" -> DateRow("Last day", d.end) { d.end = it }
        "DOSES" -> Field(d.doses, { d.doses = it.filter(Char::isDigit) }, "Total number of doses", number = true)
    }
    Txt("Marking a dose as taken never ends the course. The medicine is archived automatically only when the days, date or dose limit is reached.",
        T.c2, PT.Grey, Modifier.padding(top = 16.dp))
}

@Composable private fun StepReview(d: Draft, h24: Boolean, jump: (Int) -> Unit) {
    val m = d.toMedicine()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PillGlyph(d.shape, Color(d.cL), Color(d.cR), Color(d.cB), 88.dp)
        Txt(m.name.ifBlank { "Unnamed" }, T.h2, modifier = Modifier.padding(top = 8.dp))
        Txt(detailOf(m), T.b3, PT.Grey)
    }
    @Composable fun row(label: String, value: String, step: Int) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(PT.Surface).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Txt(label, T.c1, PT.Grey); Txt(value, T.b2) }
            TextButton({ jump(step) }) { Txt("Edit", T.c1, PT.Link) }
        }
    }
    row("Name", m.name, 0)
    row("Schedule", freqText(m, h24), 5)
    if (d.sched != "AS_NEEDED") row("Duration", durationText(m), 7)
    Txt("Optional Details", T.b1, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    Field(d.notes, { d.notes = it }, "Notes", singleLine = false, minLines = 3)
    Spacer(Modifier.height(8.dp))
    Field(d.stock, { d.stock = it.filter(Char::isDigit) }, "Pills in stock (for refill alerts)", number = true)
    Spacer(Modifier.height(8.dp))
    Field(d.refillAt, { d.refillAt = it.filter(Char::isDigit) }, "Alert me when this many are left", number = true)
}
