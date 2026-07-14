package com.elaalyawm.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elaalyawm.ElaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ElaApplication
                app.scheduler.scheduleFor(app.repository.activeDate())
            } finally { result.finish() }
        }
    }
}

