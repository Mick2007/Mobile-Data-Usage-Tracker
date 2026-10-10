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
            val fileName = "dataguard_network_report_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)

            file.bufferedWriter().use { writer ->
                // Write header
                writer.write("Timestamp,Formatted Date,Period,Mobile TX (B),Mobile RX (B),Wi-Fi TX (B),Wi-Fi RX (B),Total Mobile (MB),Total Wi-Fi (MB),Total Upload (MB),Total Download (MB),Grand Total (MB)\n")

                // Write rows
                for (usage in usageList) {
                    val dateStr = DataFormatter.formatDateTime(usage.timestamp)
                    val mobileMB = (usage.totalMobileTx + usage.totalMobileRx) / (1024.0 * 1024.0)
                    val wifiMB = (usage.totalWifiTx + usage.totalWifiRx) / (1024.0 * 1024.0)
                    val txMB = (usage.totalMobileTx + usage.totalWifiTx) / (1024.0 * 1024.0)
                    val rxMB = (usage.totalMobileRx + usage.totalWifiRx) / (1024.0 * 1024.0)
                    val totalMB = (usage.totalBytes) / (1024.0 * 1024.0)

                    writer.write(
                        "${usage.timestamp},\"$dateStr\",${usage.period}," +
                                "${usage.totalMobileTx},${usage.totalMobileRx}," +
                                "${usage.totalWifiTx},${usage.totalWifiRx}," +
                                "${String.format(Locale.US, "%.2f", mobileMB)}," +
                                "${String.format(Locale.US, "%.2f", wifiMB)}," +
                                "${String.format(Locale.US, "%.2f", txMB)}," +
                                "${String.format(Locale.US, "%.2f", rxMB)}," +
                                "${String.format(Locale.US, "%.2f", totalMB)}\n"
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
                putExtra(Intent.EXTRA_SUBJECT, "DataGuard Network Telemetry Report")
                putExtra(Intent.EXTRA_TEXT, "Here is your exported mobile and Wi-Fi data analytics audit log from DataGuard.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Network Usage Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
