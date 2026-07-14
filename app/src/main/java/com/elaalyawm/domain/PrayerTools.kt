package com.elaalyawm.domain

import android.icu.util.IslamicCalendar
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.elaalyawm.data.PrayerName
import com.elaalyawm.data.PrayerTimeEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object TrackingPeriod {
    val start: LocalDate = LocalDate.of(2026, 7, 15)
    val endExclusive: LocalDate = LocalDate.of(2027, 7, 16)
    val finalDate: LocalDate = LocalDate.of(2027, 7, 15)
    val zone: ZoneId = ZoneId.of("Africa/Cairo")

    fun allowsWriting(date: LocalDate): Boolean = date >= start && date < endExclusive
    fun isComplete(now: LocalDate): Boolean = now >= endExclusive
    fun key(date: LocalDate): String = date.toString()
    fun parse(key: String): LocalDate = LocalDate.parse(key)
}

object ArabicDates {
    private val gregorianFormatter = DateTimeFormatter.ofPattern("EEEE، d MMMM uuuu", Locale("ar", "EG"))
    private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("ar", "EG"))

    fun gregorian(date: LocalDate): String = date.format(gregorianFormatter)
    fun short(date: LocalDate): String = date.format(shortFormatter)
    fun hijri(date: LocalDate): String {
        val calendar = IslamicCalendar(Locale("ar", "EG"))
        calendar.timeInMillis = date.atStartOfDay(TrackingPeriod.zone).toInstant().toEpochMilli()
        val day = calendar.get(IslamicCalendar.DAY_OF_MONTH)
        val month = calendar.get(IslamicCalendar.MONTH)
        val year = calendar.get(IslamicCalendar.YEAR)
        val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        return "$day ${months[month]} $year هـ"
    }
}

object PrayerTimeCalculator {
    fun calculate(date: LocalDate, latitude: Double, longitude: Double): PrayerTimeEntity {
        val parameters = CalculationMethod.EGYPTIAN.parameters
        val prayerTimes = PrayerTimes(
            Coordinates(latitude, longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            parameters
        )
        return PrayerTimeEntity(
            date = TrackingPeriod.key(date), latitude = latitude, longitude = longitude,
            fajrTime = prayerTimes.fajr.time,
            dhuhrTime = prayerTimes.dhuhr.time,
            asrTime = prayerTimes.asr.time,
            maghribTime = prayerTimes.maghrib.time,
            ishaTime = prayerTimes.isha.time
        )
    }

    fun display(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(TrackingPeriod.zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

    fun ordered(times: PrayerTimeEntity): List<Pair<PrayerName, Long>> = PrayerName.ordered.map { it to times.timeFor(it) }
}

