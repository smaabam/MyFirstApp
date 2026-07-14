package com.elaalyawm.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elaalyawm.ElaApplication
import com.elaalyawm.MainActivity
import com.elaalyawm.R
import com.elaalyawm.data.PrayerName
import com.elaalyawm.domain.TrackingPeriod
import com.elaalyawm.widget.updatePrayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ElaApplication
                val date = intent.getStringExtra(NotificationScheduler.EXTRA_DATE)?.let(LocalDate::parse) ?: return@launch
                if (intent.action == ACTION_REFRESH) {
                    app.scheduler.scheduleFor(date)
                    updatePrayerWidget(context)
                    return@launch
                }
                val prayer = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER)?.let(PrayerName::valueOf) ?: return@launch
                val warning = intent.getBooleanExtra(NotificationScheduler.EXTRA_WARNING, false)
                val alreadyLogged = app.database.prayerDao().getForDate(date.toString()).any { it.prayerName == prayer && it.columnIndex == 1 && it.isChecked }
                if (!warning || !alreadyLogged) showNotification(context, prayer, warning)
                updatePrayerWidget(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, prayer: PrayerName, warning: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_IMMUTABLE)
        val text = if (warning) "تنبيه: وقت صلاة ${prayer.arabic} على وشك الانتهاء" else "حان وقت صلاة ${prayer.arabic}"
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("إلى اليوم")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 180))
            .build()
        context.getSystemService(NotificationManager::class.java).notify((prayer.ordinal + if (warning) 100 else 0), notification)
    }

    companion object {
        const val ACTION_PRAYER = "com.elaalyawm.PRAYER_ALARM"
        const val ACTION_REFRESH = "com.elaalyawm.FAJR_REFRESH"
    }
}
