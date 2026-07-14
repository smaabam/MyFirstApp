package com.elaalyawm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

class DbConverters {
    @TypeConverter fun prayerToString(value: PrayerName?): String? = value?.name
    @TypeConverter fun stringToPrayer(value: String?): PrayerName? = value?.let(PrayerName::valueOf)
    @TypeConverter fun statusToString(value: EntryStatus?): String? = value?.name
    @TypeConverter fun stringToStatus(value: String?): EntryStatus? = value?.let(EntryStatus::valueOf)
}

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_entries WHERE date = :date ORDER BY prayerName, columnIndex")
    fun observeForDate(date: String): Flow<List<PrayerEntryEntity>>

    @Query("SELECT * FROM prayer_entries WHERE date = :date")
    suspend fun getForDate(date: String): List<PrayerEntryEntity>

    @Query("SELECT * FROM prayer_entries ORDER BY date, prayerName, columnIndex")
    fun observeAll(): Flow<List<PrayerEntryEntity>>

    @Query("SELECT * FROM prayer_entries ORDER BY date, prayerName, columnIndex")
    suspend fun getAll(): List<PrayerEntryEntity>

    @Query("DELETE FROM prayer_entries")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PrayerEntryEntity): Long

    @Query("DELETE FROM prayer_entries WHERE date = :date AND prayerName = :prayer AND columnIndex = :column")
    suspend fun clearCell(date: String, prayer: PrayerName, column: Int)
}

@Dao
interface PrayerTimeDao {
    @Query("SELECT * FROM prayer_times WHERE date = :date")
    fun observe(date: String): Flow<PrayerTimeEntity?>

    @Query("SELECT * FROM prayer_times WHERE date = :date")
    suspend fun get(date: String): PrayerTimeEntity?

    @Query("SELECT * FROM prayer_times ORDER BY date DESC LIMIT 1")
    suspend fun latest(): PrayerTimeEntity?

    @Query("SELECT * FROM prayer_times ORDER BY date")
    suspend fun getAll(): List<PrayerTimeEntity>

    @Query("DELETE FROM prayer_times")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: PrayerTimeEntity)
}

@Dao
interface FawaaDao {
    @Query("SELECT * FROM fawaa_items ORDER BY missedDate DESC, id DESC")
    fun observeAll(): Flow<List<FawaaItemEntity>>

    @Query("SELECT * FROM fawaa_items ORDER BY missedDate DESC, id DESC")
    suspend fun getAll(): List<FawaaItemEntity>

    @Insert suspend fun insert(value: FawaaItemEntity): Long
    @Delete suspend fun delete(value: FawaaItemEntity)
    @Query("DELETE FROM fawaa_items") suspend fun clearAll()
}

@Database(entities = [PrayerEntryEntity::class, PrayerTimeEntity::class, FawaaItemEntity::class], version = 1, exportSchema = true)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao
    abstract fun prayerTimeDao(): PrayerTimeDao
    abstract fun fawaaDao(): FawaaDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, "ela_al_yawm.db"
        ).fallbackToDestructiveMigration().build()
    }
}
