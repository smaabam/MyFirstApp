package com.elaalyawm.backup

import android.accounts.Account
import android.content.Context
import androidx.room.withTransaction
import com.elaalyawm.data.AppDatabase
import com.elaalyawm.data.EntryStatus
import com.elaalyawm.data.FawaaItemEntity
import com.elaalyawm.data.PrayerEntryEntity
import com.elaalyawm.data.PrayerName
import com.elaalyawm.data.PrayerTimeEntity
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable private data class BackupEntry(val date: String, val prayer: String, val column: Int, val checked: Boolean, val status: String?, val timestamp: Long?, val note: String?)
@Serializable private data class BackupTime(val date: String, val latitude: Double, val longitude: Double, val fajr: Long, val dhuhr: Long, val asr: Long, val maghrib: Long, val isha: Long)
@Serializable private data class BackupFawaa(val prayer: String, val missedDate: String, val madeUpTime: Long?, val note: String?)
@Serializable private data class BackupPayload(val version: Int = 1, val createdAt: Long, val entries: List<BackupEntry>, val times: List<BackupTime>, val fawaa: List<BackupFawaa>)

/** Backs up only into Drive's private appDataFolder; no user files are enumerated. */
class DriveBackup(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upload(account: Account): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = BackupPayload(
                createdAt = System.currentTimeMillis(),
                entries = db.prayerDao().getAll().map { BackupEntry(it.date, it.prayerName.name, it.columnIndex, it.isChecked, it.status?.name, it.timestamp, it.note) },
                times = db.prayerTimeDao().getAll().map { BackupTime(it.date, it.latitude, it.longitude, it.fajrTime, it.dhuhrTime, it.asrTime, it.maghribTime, it.ishaTime) },
                fawaa = db.fawaaDao().getAll().map { BackupFawaa(it.prayerName.name, it.missedDate, it.madeUpTime, it.note) }
            )
            val service = service(account)
            val metadata = File().setName("ela-al-yawm-backup.json").setParents(listOf("appDataFolder"))
            val content = ByteArrayContent.fromString("application/json", json.encodeToString(payload))
            service.files().create(metadata, content).setFields("id").execute()
            Instant.ofEpochMilli(payload.createdAt).toString()
        }
    }

    suspend fun restoreLatest(account: Account): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val service = service(account)
            val latest = service.files().list().setSpaces("appDataFolder")
                .setQ("name = 'ela-al-yawm-backup.json' and trashed = false")
                .setOrderBy("createdTime desc").setPageSize(1).execute().files.firstOrNull()
                ?: error("لا توجد نسخة احتياطية")
            val payload = service.files().get(latest.id).executeMediaAsInputStream().bufferedReader().use { json.decodeFromString<BackupPayload>(it.readText()) }
            db.withTransaction { restorePayload(payload) }
            "تمت استعادة نسخة ${Instant.ofEpochMilli(payload.createdAt)}"
        }
    }

    private suspend fun restorePayload(payload: BackupPayload) {
        db.prayerDao().clearAll()
        db.prayerTimeDao().clearAll()
        db.fawaaDao().clearAll()
        payload.entries.forEach {
            db.prayerDao().upsert(PrayerEntryEntity(date = it.date, prayerName = PrayerName.valueOf(it.prayer), columnIndex = it.column, isChecked = it.checked, status = it.status?.let(EntryStatus::valueOf), timestamp = it.timestamp, note = it.note))
        }
        payload.times.forEach {
            db.prayerTimeDao().upsert(PrayerTimeEntity(it.date, it.latitude, it.longitude, it.fajr, it.dhuhr, it.asr, it.maghrib, it.isha))
        }
        payload.fawaa.forEach {
            db.fawaaDao().insert(FawaaItemEntity(prayerName = PrayerName.valueOf(it.prayer), missedDate = it.missedDate, madeUpTime = it.madeUpTime, note = it.note))
        }
    }

    private fun service(account: Account): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA)).apply { selectedAccount = account }
        return Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("إلى اليوم").build()
    }
}
