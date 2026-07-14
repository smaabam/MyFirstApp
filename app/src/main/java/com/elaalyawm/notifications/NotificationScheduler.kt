package com.elaalyawm.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elaalyawm.data.PrayerName
import com.elaalyawm.data.PrayerRepository
import com.elaalyawm.data.PrayerTimeEntity
import com.elaalyawm.domain.TrackingPeriod
import java.time.Instant
import java.time.LocalDate

class NotificationScheduler(private val context: Context, private val repository: PrayerRepository) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "تنبيهات الصلاة", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تنبيهات صامتة لمواقيت الصلاة"
            setSound(null, null)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Schedules five start notifications, five conditional warnings, and the next Fajr refresh. */
    suspend fun scheduleFor(date: LocalDate) {
        if (!TrackingPeriod.allowsWriting(date)) return
        val today = repository.ensurePrayerTimes(date) ?: return
        val tomorrow = repository.ensurePrayerTimes(date.plusDays(1))
        PrayerName.ordered.forEachIndexed { index, prayer ->
            val start = today.timeFor(prayer)
            schedule(start, requestCode(date, index, false), PrayerAlarmReceiver.ACTION_PRAYER, date, prayer, false)
            val end = when (prayer) {
                PrayerName.FAJR -> today.dhuhrTime
                PrayerName.DHUHR -> today.asrTime
                PrayerName.ASR -> today.maghribTime
                PrayerName.MAGHRIB -> today.ishaTime
                PrayerName.ISHA -> tomorrow?.fajrTime ?: return@forEachIndexed
            }
            schedule(end - FIFTEEN_MINUTES, requestCode(date, index, true), PrayerAlarmReceiver.ACTION_PRAYER, date, prayer, true)
        }
        tomorrow?.fajrTime?.let { schedule(it, requestCode(date.plusDays(1), 9, false), PrayerAlarmReceiver.ACTION_REFRESH, date.plusDays(1), null, false) }
    }

    private fun schedule(time: Long, code: Int, action: String, date: LocalDate, prayer: PrayerName?, warning: Boolean) {
        if (time <= System.currentTimeMillis()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_PRAYER, prayer?.name)
            putExtra(EXTRA_WARNING, warning)
        }
        val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
    }

    private fun requestCode(date: LocalDate, prayerIndex: Int, warning: Boolean): Int = (date.toEpochDay() * 20 + prayerIndex * 2 + if (warning) 1 else 0).toInt()

    companion object {
        const val CHANNEL_ID = "prayer_reminders"
        const val EXTRA_DATE = "date"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_WARNING = "warning"
        private const val FIFTEEN_MINUTES = 15 * 60 * 1000L
    }
}

