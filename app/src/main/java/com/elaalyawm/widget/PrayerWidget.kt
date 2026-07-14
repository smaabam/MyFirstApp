package com.elaalyawm.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.clickable
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.elaalyawm.ElaApplication
import com.elaalyawm.MainActivity
import com.elaalyawm.data.EntryStatus
import com.elaalyawm.data.PrayerName
import com.elaalyawm.domain.PrayerTimeCalculator
import com.elaalyawm.domain.TrackingPeriod
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class PrayerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ElaApplication
        val date = app.repository.activeDate()
        val entries = app.repository.entries(date).first()
        val times = app.repository.timeFor(date)
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(Color(0xFF0F0F0F)).padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text("إلى اليوم", style = TextStyle(color = ColorProvider(Color(0xFFE8E8E8))))
                PrayerName.ordered.forEach { prayer ->
                    val entry = entries.firstOrNull { it.prayerName == prayer && it.columnIndex == 1 }
                    val mark = when (entry?.status) { EntryStatus.ON_TIME -> "✓"; EntryStatus.DELAYED -> "✓ متأخرة"; else -> "○" }
                    val time = times?.let { PrayerTimeCalculator.display(it.timeFor(prayer)) }.orEmpty()
                    Text("${prayer.arabic}  $mark  $time", style = TextStyle(color = ColorProvider(if (entry?.status == EntryStatus.ON_TIME) Color(0xFF00C896) else Color(0xFFE8E8E8))))
                }
            }
        }
    }
}

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PrayerWidget() }

suspend fun updatePrayerWidget(context: Context) { PrayerWidget().updateAll(context) }
