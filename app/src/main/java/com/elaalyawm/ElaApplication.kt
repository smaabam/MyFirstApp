package com.elaalyawm

import android.app.Application
import com.elaalyawm.data.AppDatabase
import com.elaalyawm.data.PrayerRepository
import com.elaalyawm.notifications.NotificationScheduler

class ElaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { PrayerRepository(database, this) }
    val scheduler by lazy { NotificationScheduler(this, repository) }

    override fun onCreate() {
        super.onCreate()
        scheduler.createChannel()
    }
}

