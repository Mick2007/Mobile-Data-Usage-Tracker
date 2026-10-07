package com.example.dataguard.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.dataguard.data.entity.UsageEntity
import java.io.File
import java.util.Locale

class CsvExporter(
    private val context: Context
) {
    fun exportToCSV(usageList: List<UsageEntity>): Uri? {
        return try {
            val fileName = "dataguard_report_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)

            file.bufferedWriter().use { writer ->
                // Write header
                writer.write("Timestamp,Formatted Date,Period,TX (Bytes),RX (Bytes),Total (Bytes),Total (MB)\n")

                // Write rows
                for (usage in usageList) {
                    val dateStr = DataFormatter.formatDateTime(usage.timestamp)
                    val totalBytes = usage.totalMobileTx + usage.totalMobileRx
                    val totalMB = totalBytes / (1024.0 * 1024.0)
                    writer.write(
                        "${usage.timestamp},\"$dateStr\",${usage.period},${usage.totalMobileTx},${usage.totalMobileRx},$totalBytes,${String.format(Locale.US, "%.2f", totalMB)}\n"
                    )
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareCSV(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "DataGuard Mobile Usage Report")
                putExtra(Intent.EXTRA_TEXT, "Here is your exported mobile data analytics report from DataGuard.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Data Usage Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
