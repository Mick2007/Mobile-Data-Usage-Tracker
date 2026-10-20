package com.example.dataguard.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.data.entity.UsageEntity
import java.io.File
import java.util.Locale

/**
 * Data container for comprehensive website & telemetry export report.
 */
data class WebsiteExportReportData(
    val title: String = "DataGuard Website & Telemetry Audit Report",
    val periodLabel: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val totalUploadBytes: Long,
    val totalDownloadBytes: Long,
    val totalBytes: Long,
    val domainItems: List<ExportDomainItem>,
    val appItems: List<ExportAppItem>,
    val flowRecords: List<NetworkFlowEntity> = emptyList()
)

data class ExportDomainItem(
    val domain: String,
    val appName: String,
    val packageName: String,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val connectionCount: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val confidence: String,
    val firewallStatus: String
)

data class ExportAppItem(
    val appName: String,
    val packageName: String,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val domainCount: Int
)

class CsvExporter(
    private val context: Context
) {
    /**
     * Generates a comprehensive CSV report containing:
     * 1. Summary Totals & Report Metadata
     * 2. Per-Domain Telemetry Breakdown
     * 3. Per-App Bandwidth Usage Breakdown
     * 4. Granular Network Flow Records
     */
    fun exportComprehensiveWebsiteReport(report: WebsiteExportReportData): Uri? {
        return try {
            val fileName = "dataguard_website_report_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)

            file.bufferedWriter().use { writer ->
                writer.write(formatReportCsv(report))
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

    /**
     * Backward-compatible simple export for legacy usage records.
     */
    fun exportToCSV(usageList: List<UsageEntity>): Uri? {
        return try {
            val fileName = "dataguard_network_report_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)

            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,Formatted Date,Period,Mobile TX (B),Mobile RX (B),Wi-Fi TX (B),Wi-Fi RX (B),Total Mobile (MB),Total Wi-Fi (MB),Total Upload (MB),Total Download (MB),Grand Total (MB)\n")

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

    fun shareCSV(uri: Uri, subject: String = "DataGuard Network & Website Telemetry Report") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Here is your exported DataGuard network and website telemetry audit report.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Telemetry Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun escapeCsv(value: String): String {
            return value.replace("\"", "\"\"")
        }

        fun formatReportCsv(report: WebsiteExportReportData): String {
            val sb = StringBuilder()
            // SECTION 1: HEADER & AUDIT METADATA
            sb.append("================================================================================\n")
            sb.append("${report.title}\n")
            sb.append("================================================================================\n")
            sb.append("Export Period,\"${report.periodLabel}\"\n")
            sb.append("Start Date,\"${DataFormatter.formatDateTime(report.startTimeMillis)}\"\n")
            sb.append("End Date,\"${DataFormatter.formatDateTime(report.endTimeMillis)}\"\n")
            sb.append("Generated Timestamp,\"${DataFormatter.formatDateTime(System.currentTimeMillis())}\"\n")
            sb.append("\n")

            // SECTION 2: TOTALS & SUMMARY METRICS
            sb.append("--- SUMMARY TOTALS ---\n")
            val totalUploadMB = report.totalUploadBytes / (1024.0 * 1024.0)
            val totalDownloadMB = report.totalDownloadBytes / (1024.0 * 1024.0)
            val totalMB = report.totalBytes / (1024.0 * 1024.0)
            sb.append("Metric,Bytes,Megabytes (MB)\n")
            sb.append("Total Upload,${report.totalUploadBytes},${String.format(Locale.US, "%.2f", totalUploadMB)}\n")
            sb.append("Total Download,${report.totalDownloadBytes},${String.format(Locale.US, "%.2f", totalDownloadMB)}\n")
            sb.append("Grand Total Traffic,${report.totalBytes},${String.format(Locale.US, "%.2f", totalMB)}\n")
            sb.append("Distinct Domains Tracked,${report.domainItems.size},N/A\n")
            sb.append("Distinct Apps Active,${report.appItems.size},N/A\n")
            sb.append("\n")

            // SECTION 3: PER-DOMAIN TELEMETRY BREAKDOWN
            sb.append("--- DOMAIN ACTIVITY BREAKDOWN ---\n")
            sb.append("Rank,Domain,Primary App,Package Name,Upload (Bytes),Download (Bytes),Total (Bytes),Total (MB),Connections,First Seen,Last Seen,Confidence,Firewall Status\n")
            report.domainItems.forEachIndexed { index, d ->
                val domainMB = d.totalBytes / (1024.0 * 1024.0)
                val firstSeenStr = DataFormatter.formatDateTime(d.firstSeen)
                val lastSeenStr = DataFormatter.formatDateTime(d.lastSeen)
                sb.append(
                    "${index + 1},\"${escapeCsv(d.domain)}\",\"${escapeCsv(d.appName)}\",\"${escapeCsv(d.packageName)}\"," +
                            "${d.uploadBytes},${d.downloadBytes},${d.totalBytes}," +
                            "${String.format(Locale.US, "%.2f", domainMB)},${d.connectionCount}," +
                            "\"$firstSeenStr\",\"$lastSeenStr\",\"${d.confidence}\",\"${d.firewallStatus}\"\n"
                )
            }
            sb.append("\n")

            // SECTION 4: PER-APP USAGE BREAKDOWN
            sb.append("--- PER-APP DATA USAGE BREAKDOWN ---\n")
            sb.append("Rank,Application Name,Package Name,Upload (Bytes),Download (Bytes),Total (Bytes),Total (MB),Domains Visited\n")
            report.appItems.forEachIndexed { index, app ->
                val appMB = app.totalBytes / (1024.0 * 1024.0)
                sb.append(
                    "${index + 1},\"${escapeCsv(app.appName)}\",\"${escapeCsv(app.packageName)}\"," +
                            "${app.uploadBytes},${app.downloadBytes},${app.totalBytes}," +
                            "${String.format(Locale.US, "%.2f", appMB)},${app.domainCount}\n"
                )
            }
            sb.append("\n")

            // SECTION 5: DETAILED NETWORK FLOW LOGS
            if (report.flowRecords.isNotEmpty()) {
                sb.append("--- DETAILED NETWORK FLOW LOGS ---\n")
                sb.append("Flow Key,Domain,App Name,Package Name,Destination IP,Protocol,Network Type,Upload (Bytes),Download (Bytes),Total (Bytes),Last Seen\n")
                report.flowRecords.forEach { flow ->
                    sb.append(
                        "\"${escapeCsv(flow.flowKey)}\",\"${escapeCsv(flow.domain)}\",\"${escapeCsv(flow.appName)}\"," +
                                "\"${escapeCsv(flow.packageName)}\",\"${flow.destinationIp}\",\"${flow.protocol}\"," +
                                "\"${flow.networkType}\",${flow.uploadBytes},${flow.downloadBytes},${flow.totalBytes}," +
                                "\"${DataFormatter.formatDateTime(flow.lastSeen)}\"\n"
                    )
                }
            }
            return sb.toString()
        }
    }
}
