package com.elaalyawm.data

import android.content.Context
import com.batoulapps.adhan.Coordinates
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.elaalyawm.domain.PrayerTimeCalculator
import com.elaalyawm.domain.TrackingPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.resume

/** Single source of truth for durable prayer data. All database writes pass through here. */
class PrayerRepository(private val db: AppDatabase, context: Context) {
    private val locations: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val prayers = db.prayerDao()
    private val times = db.prayerTimeDao()
    private val fawaa = db.fawaaDao()

    fun entries(date: LocalDate): Flow<List<PrayerEntryEntity>> = prayers.observeForDate(date.toString())
    fun allEntries(): Flow<List<PrayerEntryEntity>> = prayers.observeAll()
    fun times(date: LocalDate): Flow<PrayerTimeEntity?> = times.observe(date.toString())
    fun fawaaItems(): Flow<List<FawaaItemEntity>> = fawaa.observeAll()
    suspend fun allEntriesNow() = prayers.getAll()
    suspend fun fawaaNow() = fawaa.getAll()

    suspend fun timeFor(date: LocalDate): PrayerTimeEntity? = times.get(date.toString())

    /** Uses a cached calculation when possible and the device's last known location otherwise. */
    suspend fun ensurePrayerTimes(date: LocalDate): PrayerTimeEntity? {
        times.get(date.toString())?.let { return it }
        val location = lastKnownCoordinates() ?: times.latest()?.let { Coordinates(it.latitude, it.longitude) } ?: return null
        return PrayerTimeCalculator.calculate(date, location.latitude, location.longitude).also(times::upsert)
    }

    suspend fun activeDate(now: Long = System.currentTimeMillis()): LocalDate {
        val civil = Instant.ofEpochMilli(now).atZone(TrackingPeriod.zone).toLocalDate()
        val todayTimes = ensurePrayerTimes(civil)
        return if (todayTimes != null && now < todayTimes.fajrTime) civil.minusDays(1) else civil
    }

    suspend fun recordDaily(
        date: LocalDate,
        prayer: PrayerName,
        timestamp: Long,
        note: String? = null
    ): Result<EntryStatus> = runCatching {
        check(TrackingPeriod.allowsWriting(date)) { "انتهت فترة التسجيل أو لم تبدأ بعد" }
        val existing = prayers.getForDate(date.toString()).firstOrNull { it.prayerName == prayer && it.columnIndex == 1 }
        check(existing == null) { "تم تسجيل هذه الصلاة بالفعل" }
        val dayTimes = ensurePrayerTimes(date) ?: error("تعذر حساب أوقات الصلاة. فعّل الموقع ثم أعد المحاولة.")
        val start = dayTimes.timeFor(prayer)
        check(timestamp >= start) { "لم يدخل وقت ${prayer.arabic} بعد" }
        val next = nextPrayerStart(date, prayer, dayTimes)
        val status = if (timestamp < next) EntryStatus.ON_TIME else EntryStatus.DELAYED
        prayers.upsert(PrayerEntryEntity(
            date = date.toString(), prayerName = prayer, columnIndex = 1, isChecked = true,
            status = status, timestamp = timestamp, note = note?.trim()?.takeIf(String::isNotBlank)
        ))
        status
    }

    suspend fun toggleQadaa(date: LocalDate, prayer: PrayerName, column: Int) {
        require(column in 2..5)
        check(TrackingPeriod.allowsWriting(date)) { "هذه الفترة للعرض فقط" }
        val current = prayers.getForDate(date.toString()).firstOrNull { it.prayerName == prayer && it.columnIndex == column }
        if (current?.isChecked == true) prayers.clearCell(date.toString(), prayer, column)
        else prayers.upsert(PrayerEntryEntity(date = date.toString(), prayerName = prayer, columnIndex = column, isChecked = true, status = EntryStatus.QADAA))
    }

    suspend fun saveNote(date: LocalDate, prayer: PrayerName, note: String) {
        val current = prayers.getForDate(date.toString()).firstOrNull { it.prayerName == prayer && it.columnIndex == 1 }
            ?: error("سجّل الصلاة أولاً قبل إضافة الملاحظة")
        prayers.upsert(current.copy(note = note.trim().take(240).takeIf(String::isNotBlank)))
    }

    suspend fun addFawaa(value: FawaaItemEntity) = fawaa.insert(value)
    suspend fun deleteFawaa(value: FawaaItemEntity) = fawaa.delete(value)

    private suspend fun nextPrayerStart(date: LocalDate, prayer: PrayerName, today: PrayerTimeEntity): Long = when (prayer) {
        PrayerName.FAJR -> today.dhuhrTime
        PrayerName.DHUHR -> today.asrTime
        PrayerName.ASR -> today.maghribTime
        PrayerName.MAGHRIB -> today.ishaTime
        PrayerName.ISHA -> ensurePrayerTimes(date.plusDays(1))?.fajrTime
            ?: date.plusDays(1).atStartOfDay(TrackingPeriod.zone).plusHours(5).toInstant().toEpochMilli()
    }

    private suspend fun lastKnownCoordinates(): Coordinates? = suspendCancellableCoroutine { continuation ->
        try {
            locations.lastLocation
                .addOnSuccessListener { location -> if (continuation.isActive) continuation.resume(location?.let { Coordinates(it.latitude, it.longitude) }) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

