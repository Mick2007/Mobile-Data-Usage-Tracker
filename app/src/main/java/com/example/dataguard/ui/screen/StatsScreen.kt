package com.example.dataguard.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.domain.model.TimeRangeType
import com.example.dataguard.ui.component.AppDistributionDonut
import com.example.dataguard.ui.component.BarTrendChart
import com.example.dataguard.ui.viewmodel.StatsViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(factory = ViewModelFactory)
) {
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val chartPoints by viewModel.chartPoints.collectAsState()
    val appDistribution by viewModel.appDistribution.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val totalMB = viewModel.getTotalPeriodMB()
    val mobileMB = viewModel.getTotalMobileMB()
    val wifiMB = viewModel.getTotalWifiMB()
    val avgMB = viewModel.getAverageMB()
    val peakMB = viewModel.getPeakMB()

    var showCustomDateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Analytics & Trends",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Historical usage across Mobile & Wi-Fi",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Period Selector Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("stats_period_chips"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTimeRange.type == TimeRangeType.TODAY,
                        onClick = { viewModel.setTimeRangeType(TimeRangeType.TODAY) },
                        label = { Text("Today") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTimeRange.type == TimeRangeType.YESTERDAY,
                        onClick = { viewModel.setTimeRangeType(TimeRangeType.YESTERDAY) },
                        label = { Text("Yesterday") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTimeRange.type == TimeRangeType.LAST_7_DAYS,
                        onClick = { viewModel.setTimeRangeType(TimeRangeType.LAST_7_DAYS) },
                        label = { Text("Last 7 Days") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTimeRange.type == TimeRangeType.THIS_MONTH,
                        onClick = { viewModel.setTimeRangeType(TimeRangeType.THIS_MONTH) },
                        label = { Text("This Month") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTimeRange.type == TimeRangeType.CUSTOM,
                        onClick = { showCustomDateDialog = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange, contentDescription = "Range", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selectedTimeRange.type == TimeRangeType.CUSTOM) "Custom" else "Range...")
                            }
                        }
                    )
                }
            }

            // Summary Metric Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stats_summary_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SummaryMetric(
                            label = "Cellular",
                            value = formatMegabytes(mobileMB),
                            icon = Icons.Default.SignalCellularAlt,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        SummaryMetric(
                            label = "Wi-Fi",
                            value = formatMegabytes(wifiMB),
                            icon = Icons.Default.Wifi,
                            tint = Color(0xFF6366F1)
                        )
                        SummaryMetric(
                            label = "Grand Total",
                            value = formatMegabytes(totalMB),
                            icon = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = "Daily Avg: ${formatMegabytes(avgMB)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = "Peak: ${formatMegabytes(peakMB)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            // Trend Chart Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("usage_trend_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Trend",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Traffic Over Time",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "Tap bar to inspect",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    BarTrendChart(
                        points = chartPoints,
                        height = 160.dp
                    )
                }
            }

            // App Distribution Donut Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_distribution_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "App Traffic Share",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Normalized ranking of applications for selected window",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppDistributionDonut(apps = appDistribution)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Custom Date Picker Dialog
        if (showCustomDateDialog) {
            CustomDateRangeDialog(
                onDismiss = { showCustomDateDialog = false },
                onConfirm = { daysAgo ->
                    val now = System.currentTimeMillis()
                    val start = now - (daysAgo * 24 * 60 * 60 * 1000L)
                    viewModel.setCustomRange(start, now)
                    showCustomDateDialog = false
                }
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = tint
            )
        )
    }
}

@Composable
private fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (daysAgo: Int) -> Unit
) {
    val presets = listOf(
        Pair("Last 3 Days", 3),
        Pair("Last 14 Days", 14),
        Pair("Last 30 Days", 30),
        Pair("Last 60 Days", 60),
        Pair("Last 90 Days", 90)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Custom Analytics Window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select an extended historical range for telemetry charts:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                presets.forEach { (label, days) ->
                    OutlinedButton(
                        onClick = { onConfirm(days) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatMegabytes(mb: Double): String {
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
