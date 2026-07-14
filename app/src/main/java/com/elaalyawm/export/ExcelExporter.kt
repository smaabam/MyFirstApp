package com.elaalyawm.export

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.elaalyawm.data.PrayerEntryEntity
import com.elaalyawm.data.PrayerName
import com.elaalyawm.domain.PrayerTimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.time.LocalDate

object ExcelExporter {
    suspend fun export(context: Context, entries: List<PrayerEntryEntity>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("إلى اليوم")
            val headers = listOf("التاريخ", "الصلاة", "العمود", "الحالة", "الوقت", "الملاحظة")
            sheet.createRow(0).apply { headers.forEachIndexed { i, value -> createCell(i).setCellValue(value) } }
            entries.forEachIndexed { rowIndex, entry ->
                sheet.createRow(rowIndex + 1).apply {
                    createCell(0).setCellValue(entry.date)
                    createCell(1).setCellValue(entry.prayerName.arabic)
                    createCell(2).setCellValue(if (entry.columnIndex == 1) "اليوم" else "قضاء ${entry.columnIndex - 1}")
                    createCell(3).setCellValue(when (entry.status) {
                        com.elaalyawm.data.EntryStatus.ON_TIME -> "في الوقت"
                        com.elaalyawm.data.EntryStatus.DELAYED -> "متأخرة"
                        com.elaalyawm.data.EntryStatus.QADAA -> "قضاء"
                        null -> ""
                    })
                    createCell(4).setCellValue(entry.timestamp?.let(PrayerTimeCalculator::display) ?: "")
                    createCell(5).setCellValue(entry.note.orEmpty())
                }
            }
            (0..5).forEach(sheet::autoSizeColumn)
            val name = "إلى_اليوم_تصدير_${LocalDate.now()}.xlsx"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء ملف التصدير")
            context.contentResolver.openOutputStream(uri)?.use(workbook::write) ?: error("تعذر كتابة ملف التصدير")
            workbook.close()
            "Downloads/$name"
        }
    }
}

