package com.elaalyawm.ui

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elaalyawm.ElaApplication
import com.elaalyawm.backup.DriveBackup
import com.elaalyawm.data.EntryStatus
import com.elaalyawm.data.FawaaItemEntity
import com.elaalyawm.data.PrayerEntryEntity
import com.elaalyawm.data.PrayerName
import com.elaalyawm.data.PrayerRepository
import com.elaalyawm.data.PrayerTimeEntity
import com.elaalyawm.domain.TrackingPeriod
import com.elaalyawm.export.ExcelExporter
import com.elaalyawm.widget.updatePrayerWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PrayerStats(
    val expected: Int = 0,
    val onTime: Int = 0,
    val delayed: Int = 0,
    val missed: Int = 0,
    val qadaaDone: Int = 0,
    val qadaaRemaining: Int = 0,
    val elapsedDays: Int = 0,
    val remainingDays: Int = 365,
    val progress: Float = 0f,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val perPrayer: Map<PrayerName, Triple<Int, Int, Int>> = emptyMap(),
    val fawaaCount: Int = 0
)

data class DayDetail(val date: LocalDate, val entries: List<PrayerEntryEntity>, val times: PrayerTimeEntity?)

data class ElaUiState(
    val activeDate: LocalDate = LocalDate.now(TrackingPeriod.zone),
    val entries: List<PrayerEntryEntity> = emptyList(),
    val prayerTimes: PrayerTimeEntity? = null,
    val allEntries: List<PrayerEntryEntity> = emptyList(),
    val fawaa: List<FawaaItemEntity> = emptyList(),
    val stats: PrayerStats = PrayerStats(),
    val now: Long = System.currentTimeMillis(),
    val detail: DayDetail? = null,
    val message: String? = null,
    val loadingTimes: Boolean = true,
    val lastBackup: String? = null
) {
    val readOnly: Boolean get() = !TrackingPeriod.allowsWriting(activeDate)
    val allDailyDone: Boolean get() = PrayerName.ordered.all { prayer -> entries.any { it.prayerName == prayer && it.columnIndex == 1 && it.isChecked } }
}

class ElaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ElaApplication
    private val repository: PrayerRepository = app.repository
    private val backup = DriveBackup(application, app.database)
    private val _state = MutableStateFlow(ElaUiState())
    val state: StateFlow<ElaUiState> = _state.asStateFlow()
    private var dayJob: Job? = null

    init {
        viewModelScope.launch {
            val date = repository.activeDate()
            watchDay(date)
            app.scheduler.scheduleFor(date)
        }
        viewModelScope.launch { repository.allEntries().collect { updateStats(it, _state.value.fawaa) } }
        viewModelScope.launch { repository.fawaaItems().collect { updateStats(_state.value.allEntries, it) } }
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _state.value = _state.value.copy(now = now)
                // The logical day turns over at calculated Fajr even while the app stays open.
                val desiredDay = repository.activeDate(now)
                if (desiredDay != _state.value.activeDate) {
                    watchDay(desiredDay)
                    app.scheduler.scheduleFor(desiredDay)
                }
                delay(1_000)
            }
        }
    }

    /** Call after location permission changes to calculate the current day and re-arm alarms. */
    fun reloadPrayerTimes() = viewModelScope.launch {
        val date = repository.activeDate()
        watchDay(date)
        app.scheduler.scheduleFor(date)
    }

    fun markPrayer(prayer: PrayerName, timestamp: Long = System.currentTimeMillis(), note: String? = null) = viewModelScope.launch {
        val result = repository.recordDaily(_state.value.activeDate, prayer, timestamp, note)
        _state.value = _state.value.copy(message = result.fold(
            onSuccess = { if (it == EntryStatus.ON_TIME) "تم تسجيل الصلاة في وقتها" else "تم تسجيل الصلاة متأخرة" },
            onFailure = { it.message ?: "تعذر تسجيل الصلاة" }
        ))
        if (result.isSuccess) updatePrayerWidget(getApplication())
    }

    fun toggleQadaa(prayer: PrayerName, column: Int) = viewModelScope.launch {
        runCatching { repository.toggleQadaa(_state.value.activeDate, prayer, column) }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
        updatePrayerWidget(getApplication())
    }

    fun addNote(prayer: PrayerName, note: String) = viewModelScope.launch {
        runCatching { repository.saveNote(_state.value.activeDate, prayer, note) }
            .onSuccess { _state.value = _state.value.copy(message = "تم حفظ الملاحظة") }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    fun addFawaa(prayer: PrayerName, missedDate: LocalDate, madeUpTime: Long?, note: String?) = viewModelScope.launch {
        repository.addFawaa(FawaaItemEntity(prayerName = prayer, missedDate = missedDate.toString(), madeUpTime = madeUpTime, note = note?.trim()?.takeIf(String::isNotBlank)))
        _state.value = _state.value.copy(message = "تم تسجيل الفائتة")
    }

    fun deleteFawaa(item: FawaaItemEntity) = viewModelScope.launch { repository.deleteFawaa(item) }

    fun inspectDay(date: LocalDate) = viewModelScope.launch {
        if (date > LocalDate.now(TrackingPeriod.zone) || date < TrackingPeriod.start || date >= TrackingPeriod.endExclusive) return@launch
        _state.value = _state.value.copy(detail = DayDetail(date, repository.entries(date).first(), repository.timeFor(date)))
    }

    fun clearDetail() { _state.value = _state.value.copy(detail = null) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
    fun reportMessage(message: String) { _state.value = _state.value.copy(message = message) }

    fun export() = viewModelScope.launch {
        val result = ExcelExporter.export(getApplication(), repository.allEntriesNow())
        _state.value = _state.value.copy(message = result.fold({ "تم التصدير إلى $it" }, { it.message ?: "فشل التصدير" }))
    }

    fun uploadBackup(account: Account) = viewModelScope.launch {
        _state.value = _state.value.copy(message = "جارٍ الحفظ على Drive…")
        val result = backup.upload(account)
        _state.value = _state.value.copy(message = result.fold({ "تم الحفظ على Drive — $it" }, { it.message ?: "فشل النسخ الاحتياطي" }), lastBackup = result.getOrNull())
    }

    fun restoreBackup(account: Account) = viewModelScope.launch {
        _state.value = _state.value.copy(message = "جارٍ استعادة النسخة…")
        val result = backup.restoreLatest(account)
        _state.value = _state.value.copy(message = result.fold({ it }, { it.message ?: "فشلت الاستعادة" }))
    }

    private fun watchDay(date: LocalDate) {
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            repository.ensurePrayerTimes(date)
            repository.entries(date).collectLatest { entries ->
                _state.value = _state.value.copy(activeDate = date, entries = entries, prayerTimes = repository.timeFor(date), loadingTimes = false)
            }
        }
    }

    private fun updateStats(entries: List<PrayerEntryEntity>, fawaa: List<FawaaItemEntity>) {
        val today = LocalDate.now(TrackingPeriod.zone)
        val capped = when {
            today < TrackingPeriod.start -> null
            today > TrackingPeriod.finalDate -> TrackingPeriod.finalDate
            else -> today
        }
        val days = capped?.let { (ChronoUnit.DAYS.between(TrackingPeriod.start, it) + 1).toInt() } ?: 0
        val daily = entries.filter { it.columnIndex == 1 && it.isChecked }
        val onTime = daily.count { it.status == EntryStatus.ON_TIME }
        val delayed = daily.count { it.status == EntryStatus.DELAYED }
        val expected = days * 5
        val perPrayer = PrayerName.ordered.associateWith { prayer ->
            val prayerDaily = daily.filter { it.prayerName == prayer }
            Triple(prayerDaily.count { it.status == EntryStatus.ON_TIME }, prayerDaily.count { it.status == EntryStatus.DELAYED }, (days - prayerDaily.size).coerceAtLeast(0))
        }
        val fullDays = daily.groupBy { it.date }.filterValues { values -> values.map { it.prayerName }.toSet().size == 5 }.keys
        var rolling = 0; var best = 0
        if (capped != null) {
            var day = TrackingPeriod.start
            while (!day.isAfter(capped)) {
                if (day.toString() in fullDays) { rolling++; best = maxOf(best, rolling) } else rolling = 0
                day = day.plusDays(1)
            }
        }
        val qadaaDone = entries.count { it.columnIndex in 2..5 && it.isChecked }
        val qadaaExpected = days * 20
        val stats = PrayerStats(
            expected = expected, onTime = onTime, delayed = delayed, missed = (expected - daily.size).coerceAtLeast(0),
            qadaaDone = qadaaDone, qadaaRemaining = (qadaaExpected - qadaaDone).coerceAtLeast(0),
            elapsedDays = days, remainingDays = (365 - days).coerceAtLeast(0),
            progress = if (expected + qadaaExpected == 0) 0f else (daily.size + qadaaDone).toFloat() / (expected + qadaaExpected),
            streak = rolling, bestStreak = best, perPrayer = perPrayer, fawaaCount = fawaa.size
        )
        _state.value = _state.value.copy(allEntries = entries, fawaa = fawaa, stats = stats)
    }
}
