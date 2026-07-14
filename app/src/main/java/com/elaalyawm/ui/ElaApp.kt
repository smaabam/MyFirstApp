package com.elaalyawm.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elaalyawm.data.EntryStatus
import com.elaalyawm.data.FawaaItemEntity
import com.elaalyawm.data.PrayerEntryEntity
import com.elaalyawm.data.PrayerName
import com.elaalyawm.data.PrayerTimeEntity
import com.elaalyawm.domain.ArabicDates
import com.elaalyawm.domain.PrayerTimeCalculator
import com.elaalyawm.domain.TrackingPeriod
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DriveAction { UPLOAD, RESTORE }

private object Routes { const val TODAY = "today"; const val CALENDAR = "calendar"; const val STATS = "stats"; const val FAWAA = "fawaa" }

@Composable
fun ElaApp(viewModel: ElaViewModel, requestPermissions: () -> Unit, requestExactAlarms: () -> Unit, requestDrive: (DriveAction) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbars = remember { SnackbarHostState() }
    var askPermissions by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(state.message) { state.message?.let { snackbars.showSnackbar(it); viewModel.clearMessage() } }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        ElaTheme {
            if (askPermissions) PermissionRationale(onConfirm = { askPermissions = false; requestPermissions() }, onDismiss = { askPermissions = false })
            Scaffold(
                snackbarHost = { SnackbarHost(snackbars) },
                bottomBar = {
                    val current by nav.currentBackStackEntryAsState()
                    if (current?.destination?.route != Routes.FAWAA) BottomBar(current?.destination?.route ?: Routes.TODAY) { route ->
                        nav.navigate(route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }
                }
            ) { padding ->
                NavHost(navController = nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding)) {
                    composable(Routes.TODAY) {
                        if (TrackingPeriod.isComplete(LocalDate.now(TrackingPeriod.zone))) EndPeriodScreen(state)
                        else TodayScreen(state, onMark = { prayer, time, note -> viewModel.markPrayer(prayer, time, note) }, onQadaa = { prayer, column -> viewModel.toggleQadaa(prayer, column) }, onNote = { prayer, note -> viewModel.addNote(prayer, note) })
                    }
                    composable(Routes.CALENDAR) { CalendarScreen(state) { viewModel.inspectDay(it) } }
                    composable(Routes.STATS) { StatisticsScreen(state, export = { viewModel.export() }, drive = requestDrive, onFawaa = { nav.navigate(Routes.FAWAA) }, requestExactAlarms) }
                    composable(Routes.FAWAA) { FawaaScreen(state.fawaa, onBack = { nav.popBackStack() }, onAdd = { prayer, date, madeUp, note -> viewModel.addFawaa(prayer, date, madeUp, note) }, onDelete = { viewModel.deleteFawaa(it) }) }
                }
            }
            state.detail?.let { DayDetailSheet(it, viewModel::clearDetail) }
        }
    }
}

@Composable private fun PermissionRationale(onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("إعداد إلى اليوم") },
    text = { Text("نحتاج إلى الموقع لحساب مواقيت الصلاة بدقة، وإلى الإشعارات للتنبيه الصامت. تبقى بياناتك على جهازك.") },
    confirmButton = { Button(onClick = onConfirm) { Text("متابعة") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("ليس الآن") } }
)

@Composable private fun BottomBar(selected: String, navigate: (String) -> Unit) {
    NavigationBar(containerColor = Surface) {
        listOf(Triple(Routes.TODAY, "اليوم", Icons.Default.Today), Triple(Routes.CALENDAR, "التقويم", Icons.Default.CalendarMonth), Triple(Routes.STATS, "الإحصائيات", Icons.Default.ShowChart)).forEach { (route, label, icon) ->
            NavigationBarItem(selected = selected == route, onClick = { navigate(route) }, icon = { Icon(icon, label) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen(state: ElaUiState, onMark: (PrayerName, Long, String?) -> Unit, onQadaa: (PrayerName, Int) -> Unit, onNote: (PrayerName, String) -> Unit) {
    val context = LocalContext.current
    var scheduleVisible by remember { mutableStateOf(false) }
    var selectedPrayer by remember { mutableStateOf<PrayerName?>(null) }
    var actionSheet by remember { mutableStateOf(false) }
    var noteSheet by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var observedComplete by remember(state.activeDate) { mutableStateOf<Boolean?>(null) }
    var celebration by remember(state.activeDate) { mutableIntStateOf(0) }
    LaunchedEffect(state.allDailyDone) {
        if (observedComplete == null) observedComplete = state.allDailyDone
        else if (!observedComplete!! && state.allDailyDone) { celebration++; observedComplete = true }
        else if (!state.allDailyDone) observedComplete = false
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ArabicDates.gregorian(state.activeDate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(ArabicDates.hijri(state.activeDate), color = Secondary)
                }
                IconButton(onClick = { scheduleVisible = true }) { Icon(Icons.Default.Schedule, "مواقيت اليوم", tint = Emerald) }
            }
        }
        if (state.activeDate < TrackingPeriod.start) item { Notice("تبدأ المتابعة في 15 يوليو 2026.") }
        if (state.loadingTimes) item { Notice("جارٍ حساب مواقيت الصلاة…") }
        item { CountdownCard(state.prayerTimes, state.now) }
        item { PrayerGrid(state, celebration, onMark = { prayer -> onMark(prayer, System.currentTimeMillis(), null) }, onLongPress = { prayer -> selectedPrayer = prayer; actionSheet = true }, onQadaa = onQadaa) }
        item { Spacer(Modifier.height(8.dp)) }
    }
    if (scheduleVisible) PrayerScheduleSheet(state.prayerTimes) { scheduleVisible = false }
    if (actionSheet && selectedPrayer != null) ModalBottomSheet(onDismissRequest = { actionSheet = false }) {
        Text("${selectedPrayer!!.arabic}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        SheetAction("تسجيل الوقت الحالي تلقائياً") { onMark(selectedPrayer!!, System.currentTimeMillis(), null); actionSheet = false }
        SheetAction("إدخال الوقت يدوياً") {
            val current = Instant.ofEpochMilli(state.now).atZone(TrackingPeriod.zone)
            TimePickerDialog(context, { _, hour, minute ->
                val instant = LocalDateTime.of(state.activeDate.year, state.activeDate.month, state.activeDate.dayOfMonth, hour, minute).atZone(TrackingPeriod.zone).toInstant().toEpochMilli()
                onMark(selectedPrayer!!, instant, null)
            }, current.hour, current.minute, true).show(); actionSheet = false
        }
        SheetAction("إضافة ملاحظة") { actionSheet = false; noteSheet = true }
        Spacer(Modifier.height(20.dp))
    }
    if (noteSheet && selectedPrayer != null) ModalBottomSheet(onDismissRequest = { noteSheet = false }) {
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة قصيرة") }, modifier = Modifier.fillMaxWidth().padding(20.dp))
        Button(onClick = { onNote(selectedPrayer!!, note); note = ""; noteSheet = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Text("حفظ") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun SheetAction(label: String, action: () -> Unit) = TextButton(onClick = action, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
@Composable private fun Notice(text: String) = Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, EmptyCell)) { Text(text, modifier = Modifier.padding(14.dp), color = Secondary) }

@Composable private fun CountdownCard(times: PrayerTimeEntity?, now: Long) {
    val next = times?.let { PrayerName.ordered.firstOrNull { prayer -> it.timeFor(prayer) > now }?.let { prayer -> prayer to it.timeFor(prayer) } }
    Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, Emerald.copy(alpha = .45f))) {
        val message = next?.let { "الصلاة القادمة: ${it.first.arabic}" } ?: "انتهت أوقات صلوات اليوم"
        val duration = next?.second?.let { formatDuration((it - now).coerceAtLeast(0)) }.orEmpty()
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (duration.isNotEmpty()) Text("بعد $duration", color = Emerald)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1_000
    return "%02d:%02d:%02d".format(Locale.US, seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PrayerScheduleSheet(times: PrayerTimeEntity?, dismiss: () -> Unit) = ModalBottomSheet(onDismissRequest = dismiss) {
    Text("مواقيت اليوم", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    PrayerName.ordered.forEach { prayer -> Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { Text(prayer.arabic, modifier = Modifier.weight(1f)); Text(times?.let { PrayerTimeCalculator.display(it.timeFor(prayer)) } ?: "—", color = Emerald) } }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun PrayerGrid(state: ElaUiState, celebration: Int, onMark: (PrayerName) -> Unit, onLongPress: (PrayerName) -> Unit, onQadaa: (PrayerName, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text("الصلاة", modifier = Modifier.weight(1.25f), color = Secondary, textAlign = TextAlign.Center)
            listOf("اليوم", "قضاء ١", "قضاء ٢", "قضاء ٣", "قضاء ٤").forEach { Text(it, modifier = Modifier.weight(1f), color = Secondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        PrayerName.ordered.forEachIndexed { row, prayer ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(prayer.arabic, modifier = Modifier.weight(1.25f), textAlign = TextAlign.End, fontWeight = FontWeight.Medium)
                val today = state.entries.firstOrNull { it.prayerName == prayer && it.columnIndex == 1 }
                PrayerCell(entry = today, time = state.prayerTimes?.let { PrayerTimeCalculator.display(it.timeFor(prayer)) }, enabled = !state.readOnly, celebration = celebration, delayIndex = row, onClick = { onMark(prayer) }, onLongPress = { onLongPress(prayer) })
                (2..5).forEach { column ->
                    val entry = state.entries.firstOrNull { it.prayerName == prayer && it.columnIndex == column }
                    PrayerCell(entry = entry, time = null, enabled = !state.readOnly, celebration = 0, delayIndex = 0, onClick = { onQadaa(prayer, column) }, onLongPress = {})
                }
            }
        }
    }
}

@Composable
private fun RowScope.PrayerCell(entry: PrayerEntryEntity?, time: String?, enabled: Boolean, celebration: Int, delayIndex: Int, onClick: () -> Unit, onLongPress: () -> Unit) {
    var pulse by remember(celebration) { mutableStateOf(false) }
    LaunchedEffect(celebration) { if (celebration > 0) { delay(delayIndex * 85L); pulse = true; delay(220); pulse = false } }
    val base = when (entry?.status) { EntryStatus.ON_TIME -> Emerald; EntryStatus.DELAYED -> Delayed; EntryStatus.QADAA -> Qadaa; null -> EmptyCell }
    val color by animateColorAsState(if (pulse && entry?.columnIndex == 1) Emerald.copy(alpha = .68f) else base, label = "cell")
    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(color).combinedClickable(enabled = enabled && entry == null, onClick = onClick, onLongClick = onLongPress), contentAlignment = Alignment.Center) {
        if (entry?.isChecked == true) Icon(Icons.Default.Check, "مكتمل", tint = OnSurface, modifier = Modifier.size(18.dp))
        else if (time != null) Text(time, color = Secondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CalendarScreen(state: ElaUiState, inspect: (LocalDate) -> Unit) {
    val months = remember { generateSequence(YearMonth.from(TrackingPeriod.start)) { it.plusMonths(1).takeIf { next -> next <= YearMonth.from(TrackingPeriod.finalDate) } }.toList() }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("التقويم", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
        items(months) { month -> CalendarMonth(month, state.allEntries, inspect) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun CalendarMonth(month: YearMonth, entries: List<PrayerEntryEntity>, inspect: (LocalDate) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, EmptyCell)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale("ar", "EG"))), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) { listOf("ح", "ن", "ث", "ر", "خ", "ج", "س").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Secondary, style = MaterialTheme.typography.labelSmall) } }
            val leading = month.atDay(1).dayOfWeek.value % 7
            val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                    week.forEach { date ->
                        if (date == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                        else CalendarDay(date, entries, inspect)
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalendarDay(date: LocalDate, entries: List<PrayerEntryEntity>, inspect: (LocalDate) -> Unit) {
    val today = LocalDate.now(TrackingPeriod.zone)
    val futureOrInvalid = date > today || date < TrackingPeriod.start || date >= TrackingPeriod.endExclusive
    val daily = entries.filter { it.date == date.toString() && it.columnIndex == 1 && it.isChecked }
    val color = when {
        date == today -> Emerald.copy(alpha = .30f)
        daily.size == 5 && daily.all { it.status == EntryStatus.ON_TIME } -> Emerald
        daily.size == 5 -> Delayed
        daily.isNotEmpty() -> Emerald.copy(alpha = .35f)
        else -> EmptyCell
    }
    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(7.dp)).background(if (futureOrInvalid) Background else color).combinedClickable(enabled = !futureOrInvalid, onClick = { inspect(date) }, onLongClick = {}), contentAlignment = Alignment.Center) {
        Text(date.dayOfMonth.toString(), color = if (futureOrInvalid) Secondary.copy(alpha = .45f) else OnSurface, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(detail: DayDetail, dismiss: () -> Unit) = ModalBottomSheet(onDismissRequest = dismiss) {
    Text(ArabicDates.gregorian(detail.date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
    Text(ArabicDates.hijri(detail.date), color = Secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    Spacer(Modifier.height(8.dp))
    PrayerName.ordered.forEach { prayer ->
        val entry = detail.entries.firstOrNull { it.prayerName == prayer && it.columnIndex == 1 }
        val qadaa = detail.entries.count { it.prayerName == prayer && it.columnIndex in 2..5 && it.isChecked }
        val status = when (entry?.status) { EntryStatus.ON_TIME -> "✓ في الوقت"; EntryStatus.DELAYED -> "⚠ متأخرة"; else -> "✕ لم تُسجل" }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
            Row { Text(prayer.arabic, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(status, color = if (entry?.status == EntryStatus.DELAYED) Delayed else Emerald) }
            entry?.timestamp?.let { Text("وقت التسجيل: ${PrayerTimeCalculator.display(it)}", color = Secondary, style = MaterialTheme.typography.labelSmall) }
            entry?.note?.let { Text(it, color = Secondary, style = MaterialTheme.typography.labelSmall) }
            Text("قضاء: $qadaa/4", color = Secondary, style = MaterialTheme.typography.labelSmall)
        }
        Divider(color = EmptyCell)
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun StatisticsScreen(state: ElaUiState, export: () -> Unit, drive: (DriveAction) -> Unit, onFawaa: () -> Unit, requestExactAlarms: () -> Unit) {
    val stats = state.stats
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("الإحصائيات", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
        item { StatCard("الأداء اليومي") {
            StatRow("الصلوات المسجلة", "${stats.onTime + stats.delayed}/${stats.expected}")
            StatRow("في الوقت", stats.onTime.toString(), Emerald)
            StatRow("متأخرة", stats.delayed.toString(), Delayed)
            StatRow("غير مسجلة", stats.missed.toString(), Secondary)
            val pct = if (stats.expected == 0) 0 else stats.onTime * 100 / stats.expected
            StatRow("نسبة الصلاة في وقتها", "$pct%")
        } }
        item { StatCard("القضاء") {
            StatRow("المكتمل", stats.qadaaDone.toString(), Emerald)
            StatRow("المتبقي", stats.qadaaRemaining.toString())
            PrayerName.ordered.forEach { prayer ->
                val completed = state.allEntries.count { it.prayerName == prayer && it.columnIndex in 2..5 && it.isChecked }
                StatRow(prayer.arabic, "$completed/${stats.elapsedDays * 4}")
            }
        } }
        item { StatCard("التقدم العام") {
            StatRow("الأيام المنقضية", stats.elapsedDays.toString())
            StatRow("الأيام المتبقية", stats.remainingDays.toString())
            StatRow("السلسلة الحالية / الأفضل", "${stats.streak} / ${stats.bestStreak}")
            Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { stats.progress }, modifier = Modifier.fillMaxWidth(), color = Emerald)
        } }
        item { StatCard("حسب الصلاة") { PrayerName.ordered.forEach { prayer -> val (on, late, missed) = stats.perPrayer[prayer] ?: Triple(0, 0, 0); StatRow(prayer.arabic, "$on في الوقت · $late متأخرة · $missed فائتة") } } }
        item { StatCard("الفوائت المسجلة") { StatRow("الإجمالي", stats.fawaaCount.toString()); OutlinedButton(onClick = onFawaa, modifier = Modifier.fillMaxWidth()) { Text("سجل الفوائت ←") } } }
        item { Button(onClick = export, modifier = Modifier.fillMaxWidth()) { Text("تصدير البيانات (Excel)") } }
        item { OutlinedButton(onClick = { drive(DriveAction.UPLOAD) }, modifier = Modifier.fillMaxWidth()) { Text("نسخ احتياطي على Drive") } }
        item { OutlinedButton(onClick = { drive(DriveAction.RESTORE) }, modifier = Modifier.fillMaxWidth()) { Text("استعادة النسخة الاحتياطية") } }
        item { state.lastBackup?.let { Text("آخر نسخة: $it", color = Secondary, style = MaterialTheme.typography.labelSmall) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item { TextButton(onClick = requestExactAlarms, modifier = Modifier.fillMaxWidth()) { Text("السماح بالتنبيهات الدقيقة") } }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable private fun StatCard(title: String, content: @Composable () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, EmptyCell)) { Column(modifier = Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); content() } }
@Composable private fun StatRow(label: String, value: String, color: Color = OnSurface) = Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(label, modifier = Modifier.weight(1f), color = Secondary); Text(value, color = color) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FawaaScreen(items: List<FawaaItemEntity>, onBack: () -> Unit, onAdd: (PrayerName, LocalDate, Long?, String?) -> Unit, onDelete: (FawaaItemEntity) -> Unit) {
    val context = LocalContext.current
    var prayer by remember { mutableStateOf(PrayerName.FAJR) }
    var date by remember { mutableStateOf(LocalDate.now(TrackingPeriod.zone)) }
    var madeUp by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopAppBar(title = { Text("سجل الفوائت") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }
        item { Text("الصلاة") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) { PrayerName.ordered.forEach { item -> AssistChip(onClick = { prayer = item }, label = { Text(item.arabic) }, leadingIcon = if (prayer == item) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp)) }) else null) } } }
        item { OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth()) { Text("تاريخ الفائتة: ${ArabicDates.short(date)}") } }
        item { OutlinedButton(onClick = { val now = LocalDateTime.now(TrackingPeriod.zone); TimePickerDialog(context, { _, h, min -> madeUp = LocalDateTime.of(date.year, date.month, date.dayOfMonth, h, min).atZone(TrackingPeriod.zone).toInstant().toEpochMilli() }, now.hour, now.minute, true).show() }, modifier = Modifier.fillMaxWidth()) { Text(if (madeUp == null) "وقت القضاء (اختياري)" else "وقت القضاء: ${PrayerTimeCalculator.display(madeUp!!)}") } }
        item { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة (اختيارية)") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { onAdd(prayer, date, madeUp, note); madeUp = null; note = "" }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل") } }
        item { Divider(color = EmptyCell); Text("السجل", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
        items(items, key = { it.id }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = Surface), border = BorderStroke(1.dp, EmptyCell)) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("${item.prayerName.arabic} — ${ArabicDates.short(LocalDate.parse(item.missedDate))}"); item.madeUpTime?.let { Text("قُضيت: ${PrayerTimeCalculator.display(it)}", color = Secondary, style = MaterialTheme.typography.labelSmall) }; item.note?.let { Text(it, color = Secondary, style = MaterialTheme.typography.labelSmall) } }; IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, "حذف", tint = Delayed) } } }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun EndPeriodScreen(state: ElaUiState) {
    val stats = state.stats
    Column(modifier = Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("انتهت الرحلة", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text("${(stats.progress * 100).toInt()}%", style = MaterialTheme.typography.displayLarge, color = Emerald)
        Text("نسبة الإنجاز الكلية", color = Secondary)
        Spacer(Modifier.height(24.dp)); LinearProgressIndicator(progress = { stats.progress }, modifier = Modifier.fillMaxWidth(), color = Emerald)
        Spacer(Modifier.height(20.dp)); StatRow("في الوقت", stats.onTime.toString(), Emerald); StatRow("متأخرة", stats.delayed.toString(), Delayed); StatRow("غير مسجلة", stats.missed.toString()); StatRow("القضاء المكتمل", stats.qadaaDone.toString()); StatRow("أفضل سلسلة", "${stats.bestStreak} يوم")
    }
}
