package com.elaalyawm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class PrayerName(val arabic: String) {
    FAJR("الفجر"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء");
    companion object { val ordered = entries.toList() }
}

enum class EntryStatus { ON_TIME, DELAYED, QADAA }

@Entity(
    tableName = "prayer_entries",
    indices = [Index(value = ["date", "prayerName", "columnIndex"], unique = true)]
)
data class PrayerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val prayerName: PrayerName,
    val columnIndex: Int,
    val isChecked: Boolean,
    val status: EntryStatus? = null,
    val timestamp: Long? = null,
    val note: String? = null
)

@Entity(tableName = "prayer_times")
data class PrayerTimeEntity(
    @PrimaryKey val date: String,
    val latitude: Double,
    val longitude: Double,
    val fajrTime: Long,
    val dhuhrTime: Long,
    val asrTime: Long,
    val maghribTime: Long,
    val ishaTime: Long
) {
    fun timeFor(prayer: PrayerName): Long = when (prayer) {
        PrayerName.FAJR -> fajrTime
        PrayerName.DHUHR -> dhuhrTime
        PrayerName.ASR -> asrTime
        PrayerName.MAGHRIB -> maghribTime
        PrayerName.ISHA -> ishaTime
    }
}

@Entity(tableName = "fawaa_items")
data class FawaaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prayerName: PrayerName,
    val missedDate: String,
    val madeUpTime: Long? = null,
    val note: String? = null
)

data class DayRecord(
    val date: LocalDate,
    val entries: List<PrayerEntryEntity>,
    val times: PrayerTimeEntity?
)

