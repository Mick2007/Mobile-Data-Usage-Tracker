package com.example.dataguard.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.AppLimitEntity
import com.example.dataguard.data.entity.QuotaCycleType
import com.example.dataguard.data.entity.QuotaEnforcementMode
import com.example.dataguard.domain.model.AppQuotaCalculation
import com.example.dataguard.domain.model.TimeRangeType
import com.example.dataguard.ui.viewmodel.AppSortOption
import com.example.dataguard.ui.viewmodel.AppsViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import com.example.dataguard.utils.DataFormatter

@Composable
fun AppsScreen(
    viewModel: AppsViewModel = viewModel(factory = ViewModelFactory)
) {
    val allApps by viewModel.allApps.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val blockedApps by viewModel.blockedApps.collectAsState()
    val appLimits by viewModel.appLimits.collectAsState()
    val quotaCalculations by viewModel.quotaCalculations.collectAsState()
    val selectedAppForDetail by viewModel.selectedAppForDetail.collectAsState()
    val selectedAppQuotaCalculation by viewModel.selectedAppQuotaCalculation.collectAsState()
    val showDetailDialog by viewModel.showDetailDialog.collectAsState()
    val showQuotaEditor by viewModel.showQuotaEditor.collectAsState()
    val hasUsageStats by viewModel.hasUsageStatsPermission.collectAsState()
    val isNetworkStatsActive by viewModel.isFromNetworkStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showCustomDateDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val filteredApps = allApps
        .filter {
            it.totalBytes > 0L && (
                searchQuery.isBlank() ||
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            )
        }
        .let { list ->
            when (sortOption) {
                AppSortOption.TOTAL_USAGE -> list.sortedByDescending { it.totalBytes }
                AppSortOption.MOBILE_DATA -> list.sortedByDescending { it.mobileTotalBytes }
                AppSortOption.WIFI_DATA -> list.sortedByDescending { it.wifiTotalBytes }
                AppSortOption.DOWNLOAD_RX -> list.sortedByDescending { it.rxBytes }
                AppSortOption.UPLOAD_TX -> list.sortedByDescending { it.txBytes }
                AppSortOption.NAME -> list.sortedBy { it.appName.lowercase() }
            }
        }

    val maxTotalBytes = (filteredApps.maxOfOrNull { it.totalBytes } ?: 1L).coerceAtLeast(1L)
    val totalAggregateMetricBytes = filteredApps.sumOf { getAppMetricBytes(it, sortOption) }
    val topConsumerApp = filteredApps.maxByOrNull { getAppMetricBytes(it, sortOption) }
    val blockedCount = blockedApps.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Application Accounting",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "${allApps.size} apps • ${selectedTimeRange.displayLabel} • $blockedCount restricted",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            IconButton(
                onClick = { viewModel.loadApps() },
                modifier = Modifier.testTag("refresh_apps_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh apps",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Telemetry source banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (hasUsageStats) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasUsageStats) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (hasUsageStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasUsageStats && isNetworkStatsActive)
                        "NetworkStatsManager Active • Per-app Quota Engine & Firewall Ready"
                    else
                        "TrafficStats Fallback • Tap app cards to inspect details and set quotas",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (hasUsageStats) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Time Range Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf(
                Pair("Today", TimeRangeType.TODAY),
                Pair("Yesterday", TimeRangeType.YESTERDAY),
                Pair("Last 7 Days", TimeRangeType.LAST_7_DAYS),
                Pair("This Month", TimeRangeType.THIS_MONTH),
                Pair("Custom", TimeRangeType.CUSTOM)
            )

            items(chips) { (label, type) ->
                val isSelected = selectedTimeRange.type == type
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (type == TimeRangeType.CUSTOM) {
                            showCustomDateDialog = true
                        } else {
                            viewModel.setTimeRangeType(type)
                        }
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = if (type == TimeRangeType.CUSTOM) {
                        {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Custom date",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    modifier = Modifier.testTag("chip_range_${type.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Search by name or package...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sort Options Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AppSortOption.values()) { option ->
                val isSelected = sortOption == option
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setSortOption(option) },
                    label = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    },
                    modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Applications List
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("apps_empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "No apps",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No application traffic recorded for this window",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("apps_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isBlocked = viewModel.isAppBlocked(app.uid)
                    val quotaCalc = quotaCalculations[app.uid]
                    AppItemCard(
                        modifier = Modifier.animateItem(),
                        app = app,
                        sortOption = sortOption,
                        maxBytes = maxTotalBytes,
                        isBlocked = isBlocked,
                        quotaCalculation = quotaCalc,
                        onCardClick = {
                            viewModel.openAppDetail(app)
                        },
                        onBlockToggled = { shouldBlock ->
                            viewModel.toggleBlockApp(app, shouldBlock)
                        },
                        onOpenSettings = {
                            viewModel.openAppSettings(app.packageName)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Automatic Bottom Preview Summary Bar
            AppSelectionPreviewBottomBar(
                sortOption = sortOption,
                totalAggregateBytes = totalAggregateMetricBytes,
                appCount = filteredApps.size,
                topApp = topConsumerApp,
                onJumpToTop = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            )
        }

        // Custom Date Range Dialog
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

        // Application Detail Screen / Dialog
        if (showDetailDialog && selectedAppForDetail != null) {
            val app = selectedAppForDetail!!
            AppDetailDialog(
                app = app,
                quotaCalculation = selectedAppQuotaCalculation,
                isBlocked = viewModel.isAppBlocked(app.uid),
                onDismiss = { viewModel.closeAppDetail() },
                onOpenQuotaEditor = { viewModel.openQuotaEditor() },
                onResetCycle = { viewModel.resetAppQuotaCycle(app.uid) },
                onDeleteQuota = { viewModel.deleteAppQuota(app.uid) },
                onToggleQuotaEnabled = { enabled -> viewModel.toggleQuotaEnabled(app.uid, enabled) },
                onBlockToggled = { shouldBlock -> viewModel.toggleBlockApp(app, shouldBlock) },
                onOpenSettings = { viewModel.openAppSettings(app.packageName) }
            )
        }

        // Intuitive Quota Editor Dialog
        if (showQuotaEditor && selectedAppForDetail != null) {
            val app = selectedAppForDetail!!
            val currentLimit = appLimits[app.uid]
            AppQuotaEditorDialog(
                appName = app.appName,
                packageName = app.packageName,
                initialLimit = currentLimit,
                onDismiss = { viewModel.closeQuotaEditor() },
                onSave = { limitBytes, warningThreshold, enforcementMode, cycleType, cycleStartDay, customDays, isEnabled ->
                    viewModel.saveQuota(
                        limitBytes = limitBytes,
                        warningThresholdPercent = warningThreshold,
                        enforcementMode = enforcementMode,
                        cycleType = cycleType,
                        cycleStartDay = cycleStartDay,
                        customCycleDays = customDays,
                        isEnabled = isEnabled
                    )
                }
            )
        }
    }
}

@Composable
private fun AppItemCard(
    modifier: Modifier = Modifier,
    app: AppDataUsage,
    sortOption: AppSortOption = AppSortOption.TOTAL_USAGE,
    maxBytes: Long,
    isBlocked: Boolean,
    quotaCalculation: AppQuotaCalculation?,
    onCardClick: () -> Unit,
    onBlockToggled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val proportion = (app.totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0.05f, 1f)

    val progressColor = if (isBlocked) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_card_${app.packageName}")
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Icon, Name, Package, Percentage badge & Block switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconView(
                        packageName = app.packageName,
                        appName = app.appName,
                        isBlocked = isBlocked
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Usage Percentage Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = String.format("%.1f%%", app.usagePercentage),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Action Area: Settings Cog & Block Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp).testTag("app_settings_${app.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System App Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Switch(
                        checked = isBlocked,
                        onCheckedChange = onBlockToggled,
                        modifier = Modifier.testTag("block_switch_${app.packageName}"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Proportional Progress Bar scaled to selected metric
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(proportion)
                        .height(5.dp)
                        .background(progressColor)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Network and Total Usage Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary / Context Badges: Always Cell & Wi-Fi
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBadge(
                        Icons.Default.SignalCellularAlt,
                        "Cell",
                        DataFormatter.formatBytesShort(app.mobileTotalBytes),
                        MaterialTheme.colorScheme.primary
                    )
                    MetricBadge(
                        Icons.Default.Wifi,
                        "Wi-Fi",
                        DataFormatter.formatBytesShort(app.wifiTotalBytes),
                        Color(0xFF818CF8)
                    )
                }

                // Primary Large Highlighted Metric: Always Total Usage
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = DataFormatter.formatBytes(app.totalBytes),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Total Usage",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtext adapted to selected metric
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val subtext = when (sortOption) {
                    AppSortOption.UPLOAD_TX -> "↑ Upload: ${DataFormatter.formatBytesShort(app.txBytes)}   •   ↓ DL: ${DataFormatter.formatBytesShort(app.rxBytes)}"
                    AppSortOption.DOWNLOAD_RX -> "↓ Download: ${DataFormatter.formatBytesShort(app.rxBytes)}   •   ↑ UL: ${DataFormatter.formatBytesShort(app.txBytes)}"
                    AppSortOption.MOBILE_DATA -> "Cellular: ${DataFormatter.formatBytesShort(app.mobileTotalBytes)}   •   Wi-Fi: ${DataFormatter.formatBytesShort(app.wifiTotalBytes)}"
                    AppSortOption.WIFI_DATA -> "Wi-Fi: ${DataFormatter.formatBytesShort(app.wifiTotalBytes)}   •   Cellular: ${DataFormatter.formatBytesShort(app.mobileTotalBytes)}"
                    else -> "↓ DL: ${DataFormatter.formatBytesShort(app.rxBytes)}   •   ↑ UL: ${DataFormatter.formatBytesShort(app.txBytes)}"
                }
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
                if (isBlocked) {
                    Text(
                        text = "RESTRICTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Quota Indicator Pill (If configured for this app)
            if (quotaCalculation != null && quotaCalculation.limit.isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        quotaCalculation.isLimitReached -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        quotaCalculation.isWarningReached -> Color(0xFFFEF3C7)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_quota_badge_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Quota",
                                modifier = Modifier.size(14.dp),
                                tint = when {
                                    quotaCalculation.isLimitReached -> MaterialTheme.colorScheme.error
                                    quotaCalculation.isWarningReached -> Color(0xFFD97706)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quota: ${DataFormatter.formatBytesShort(quotaCalculation.usedBytes)} / ${DataFormatter.formatBytesShort(quotaCalculation.limit.limitBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Text(
                            text = when {
                                quotaCalculation.isLimitReached -> "🛑 Limit Reached"
                                quotaCalculation.isWarningReached -> "⚠️ ${quotaCalculation.percentage.toInt()}% Used"
                                else -> "${quotaCalculation.percentage.toInt()}% (${quotaCalculation.resetDateFormatted})"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = when {
                                    quotaCalculation.isLimitReached -> MaterialTheme.colorScheme.error
                                    quotaCalculation.isWarningReached -> Color(0xFFB45309)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun AppSelectionPreviewBottomBar(
    sortOption: AppSortOption,
    totalAggregateBytes: Long,
    appCount: Int,
    topApp: AppDataUsage?,
    onJumpToTop: () -> Unit
) {
    val metricColor = when (sortOption) {
        AppSortOption.UPLOAD_TX -> Color(0xFF38BDF8)
        AppSortOption.DOWNLOAD_RX -> Color(0xFF34D399)
        AppSortOption.WIFI_DATA -> Color(0xFF818CF8)
        AppSortOption.MOBILE_DATA -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val metricIcon = when (sortOption) {
        AppSortOption.UPLOAD_TX -> Icons.Default.ArrowUpward
        AppSortOption.DOWNLOAD_RX -> Icons.Default.ArrowDownward
        AppSortOption.MOBILE_DATA -> Icons.Default.SignalCellularAlt
        AppSortOption.WIFI_DATA -> Icons.Default.Wifi
        else -> Icons.Default.Speed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_selection_preview_bar"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(metricColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = metricIcon,
                        contentDescription = null,
                        tint = metricColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${getMetricLabel(sortOption)} Total: ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = DataFormatter.formatBytes(totalAggregateBytes),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = metricColor
                            )
                        )
                    }
                    if (topApp != null) {
                        val topBytes = getAppMetricBytes(topApp, sortOption)
                        Text(
                            text = "Top: ${topApp.appName} (${DataFormatter.formatBytesShort(topBytes)}) • $appCount apps",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Jump to Top Action Button
            OutlinedButton(
                onClick = onJumpToTop,
                modifier = Modifier
                    .testTag("jump_to_top_button")
                    .height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Scroll to top",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Top", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun getAppMetricBytes(app: AppDataUsage, option: AppSortOption): Long {
    return when (option) {
        AppSortOption.TOTAL_USAGE -> app.totalBytes
        AppSortOption.MOBILE_DATA -> app.mobileTotalBytes
        AppSortOption.WIFI_DATA -> app.wifiTotalBytes
        AppSortOption.DOWNLOAD_RX -> app.rxBytes
        AppSortOption.UPLOAD_TX -> app.txBytes
        AppSortOption.NAME -> app.totalBytes
    }
}

private fun getMetricLabel(option: AppSortOption): String {
    return when (option) {
        AppSortOption.TOTAL_USAGE -> "Total"
        AppSortOption.MOBILE_DATA -> "Cellular"
        AppSortOption.WIFI_DATA -> "Wi-Fi"
        AppSortOption.DOWNLOAD_RX -> "Download"
        AppSortOption.UPLOAD_TX -> "Upload"
        AppSortOption.NAME -> "Total"
    }
}

/**
 * High-fidelity Application Detail Screen Dialog presenting comprehensive network accounting,
 * live quota metrics, policy enforcement state, and quota controls.
 */
@Composable
private fun AppDetailDialog(
    app: AppDataUsage,
    quotaCalculation: AppQuotaCalculation?,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onOpenQuotaEditor: () -> Unit,
    onResetCycle: () -> Unit,
    onDeleteQuota: () -> Unit,
    onToggleQuotaEnabled: (Boolean) -> Unit,
    onBlockToggled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .testTag("app_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header: App icon, Title, Package and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIconView(
                            packageName = app.packageName,
                            appName = app.appName,
                            isBlocked = isBlocked
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "${app.packageName} • UID: ${app.uid}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_detail_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Network Usage Breakdown Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "NETWORK USAGE IN WINDOW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SignalCellularAlt,
                                        contentDescription = "Cellular",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mobile Data",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = DataFormatter.formatBytes(app.mobileTotalBytes),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "↓ ${DataFormatter.formatBytesShort(app.mobileRxBytes)} • ↑ ${DataFormatter.formatBytesShort(app.mobileTxBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = "Wi-Fi",
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Wi-Fi Network",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = DataFormatter.formatBytes(app.wifiTotalBytes),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "↓ ${DataFormatter.formatBytesShort(app.wifiRxBytes)} • ↑ ${DataFormatter.formatBytesShort(app.wifiTxBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Traffic in Range:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = DataFormatter.formatBytes(app.totalBytes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Data Quota & Policy Enforcement Section
                if (quotaCalculation != null) {
                    val calc = quotaCalculation
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                calc.isLimitReached -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                calc.isWarningReached -> Color(0xFFFEF3C7)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_quota_detail_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Section title & status badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (calc.isLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "DATA QUOTA POLICY",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        !calc.limit.isEnabled -> MaterialTheme.colorScheme.surfaceVariant
                                        calc.isLimitReached -> MaterialTheme.colorScheme.error
                                        calc.isWarningReached -> Color(0xFFD97706)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            !calc.limit.isEnabled -> "DISABLED"
                                            calc.isLimitReached -> "LIMIT REACHED"
                                            calc.isWarningReached -> "WARNING LEVEL"
                                            else -> "ACTIVE"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 7 Required Quota Metrics Grid:
                            // 1. Used, 2. Limit, 3. Remaining, 4. Percentage, 5. Reset date, 6. Warning threshold, 7. Enforcement mode

                            // Row 1: Used & Limit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Used:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = DataFormatter.formatBytes(calc.usedBytes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.testTag("quota_used_text")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Quota Limit:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = DataFormatter.formatBytes(calc.limit.limitBytes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.testTag("quota_limit_text")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Visual Progress Bar
                            val progressFraction = (calc.usedBytes.toFloat() / calc.limit.limitBytes.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(8.dp)
                                        .background(
                                            when {
                                                calc.isLimitReached -> MaterialTheme.colorScheme.error
                                                calc.isWarningReached -> Color(0xFFD97706)
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Remaining & Percentage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Remaining:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = DataFormatter.formatBytes(calc.remainingBytes),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (calc.isLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("quota_remaining_text")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Percentage:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = String.format("%.1f%%", calc.percentage),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (calc.isLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.testTag("quota_percentage_text")
                                    )
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 10.dp), thickness = 0.5.dp)

                            // Row 3: Reset Date & Warning Threshold
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Reset Date:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = calc.resetDateFormatted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.testTag("quota_reset_date_text")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Warning Threshold:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = "${calc.limit.warningThresholdPercent}% (${DataFormatter.formatBytesShort((calc.limit.limitBytes * calc.limit.warningThresholdPercent) / 100)})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.testTag("quota_warning_text")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 4: Enforcement Mode & Cycle Description
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Enforcement Mode:",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = if (calc.limit.enforcementMode == QuotaEnforcementMode.BLOCK_AT_LIMIT.name)
                                            "🛑 Block at Limit" else "⚠️ Warn Only",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (calc.limit.enforcementMode == QuotaEnforcementMode.BLOCK_AT_LIMIT.name)
                                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("quota_enforcement_text")
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (calc.limit.isEnabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = calc.limit.isEnabled,
                                        onCheckedChange = onToggleQuotaEnabled,
                                        modifier = Modifier.testTag("toggle_quota_switch")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons: Edit Quota, Reset Cycle, Delete Quota
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onResetCycle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reset_cycle_button")
                                ) {
                                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset Cycle", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = onOpenQuotaEditor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_quota_editor_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Quota", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            TextButton(
                                onClick = onDeleteQuota,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("delete_quota_button")
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove Data Quota")
                            }
                        }
                    }
                } else {
                    // No Quota Configured Yet
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Data Quota Configured",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Set a daily, weekly, or monthly quota to control cellular and Wi-Fi drain with automated warnings and firewall cutoffs.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onOpenQuotaEditor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_quota_editor_button")
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set Data Quota")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Firewall & Security Section
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "DataGuard Firewall",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (isBlocked) "Traffic Blocked by Firewall" else "Traffic Allowed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isBlocked,
                                onCheckedChange = onBlockToggled,
                                modifier = Modifier.testTag("detail_firewall_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.error,
                                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Android System Background Restrictions")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Intuitive Data Quota Editor Dialog.
 * Allows quick preset selection, custom values (MB/GB), daily/weekly/monthly/custom cycle schedules,
 * warning threshold slider, and Warn Only vs Block at Limit enforcement.
 */
@Composable
private fun AppQuotaEditorDialog(
    appName: String,
    packageName: String,
    initialLimit: AppLimitEntity?,
    onDismiss: () -> Unit,
    onSave: (
        limitBytes: Long,
        warningThreshold: Int,
        enforcementMode: QuotaEnforcementMode,
        cycleType: QuotaCycleType,
        cycleStartDay: Int,
        customDays: Int,
        isEnabled: Boolean
    ) -> Unit
) {
    // Initial values
    val defaultLimitMB = if (initialLimit != null && initialLimit.limitBytes > 0) {
        initialLimit.limitBytes / (1024 * 1024)
    } else {
        1000L // 1 GB default
    }

    var isGB by remember { mutableStateOf(defaultLimitMB >= 1000L && defaultLimitMB % 1000L == 0L) }
    var limitInput by remember {
        mutableStateOf(
            if (isGB) (defaultLimitMB / 1024L).coerceAtLeast(1L).toString()
            else defaultLimitMB.toString()
        )
    }

    var selectedCycle by remember {
        mutableStateOf(
            try {
                if (initialLimit != null) QuotaCycleType.valueOf(initialLimit.cycleType)
                else QuotaCycleType.MONTHLY
            } catch (e: Exception) {
                QuotaCycleType.MONTHLY
            }
        )
    }

    var warningThreshold by remember {
        mutableFloatStateOf(initialLimit?.warningThresholdPercent?.toFloat() ?: 80f)
    }

    var enforcementMode by remember {
        mutableStateOf(
            try {
                if (initialLimit != null) QuotaEnforcementMode.valueOf(initialLimit.enforcementMode)
                else QuotaEnforcementMode.WARN_ONLY
            } catch (e: Exception) {
                QuotaEnforcementMode.WARN_ONLY
            }
        )
    }

    var cycleStartDay by remember {
        mutableIntStateOf(initialLimit?.cycleStartDay ?: 1)
    }

    var customCycleDays by remember {
        mutableIntStateOf(initialLimit?.customCycleDays ?: 30)
    }

    var isQuotaEnabled by remember {
        mutableStateOf(initialLimit?.isEnabled ?: true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .testTag("app_quota_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Configure Quota",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quota Enabled Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Enable Data Quota for $appName",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Switch(
                        checked = isQuotaEnabled,
                        onCheckedChange = { isQuotaEnabled = it },
                        modifier = Modifier.testTag("editor_enable_quota_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Limit Presets
                Text(
                    text = "QUOTA ALLOWANCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        Pair("300 MB", 300L to false),
                        Pair("500 MB", 500L to false),
                        Pair("1 GB", 1L to true),
                        Pair("2 GB", 2L to true),
                        Pair("5 GB", 5L to true),
                        Pair("10 GB", 10L to true)
                    )
                    items(presets) { (label, config) ->
                        val (amount, inGb) = config
                        val isSelected = isGB == inGb && limitInput == amount.toString()
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                isGB = inGb
                                limitInput = amount.toString()
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Limit Input + Unit Selector (MB / GB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = limitInput,
                        onValueChange = { limitInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Allowance") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quota_amount_input")
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isGB) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { isGB = false }
                        ) {
                            Text(
                                text = "MB",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isGB) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGB) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { isGB = true }
                        ) {
                            Text(
                                text = "GB",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGB) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Billing Cycle Selection
                Text(
                    text = "CYCLE SCHEDULE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(QuotaCycleType.values()) { cycle ->
                        val isSelected = selectedCycle == cycle
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCycle = cycle },
                            label = { Text(cycle.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("cycle_chip_${cycle.name.lowercase()}")
                        )
                    }
                }

                // If Monthly: Cycle Start Day
                if (selectedCycle == QuotaCycleType.MONTHLY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Billing cycle starts on day $cycleStartDay of every month",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Slider(
                        value = cycleStartDay.toFloat(),
                        onValueChange = { cycleStartDay = it.toInt() },
                        valueRange = 1f..31f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // If Custom: Cycle Length in Days
                if (selectedCycle == QuotaCycleType.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cycle duration: $customCycleDays days before automatic reset",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Slider(
                        value = customCycleDays.toFloat(),
                        onValueChange = { customCycleDays = it.toInt() },
                        valueRange = 1f..90f,
                        steps = 88,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Warning Threshold Slider
                val parsedLimitNum = limitInput.toLongOrNull() ?: 1L
                val calculatedBytes = if (isGB) parsedLimitNum * 1024L * 1024L * 1024L else parsedLimitNum * 1024L * 1024L
                val warningBytes = (calculatedBytes * warningThreshold.toInt()) / 100

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WARNING THRESHOLD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "${warningThreshold.toInt()}% (${DataFormatter.formatBytes(warningBytes)})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                    )
                }

                Slider(
                    value = warningThreshold,
                    onValueChange = { warningThreshold = it },
                    valueRange = 50f..95f,
                    steps = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warning_threshold_slider")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Enforcement Mode Selection
                Text(
                    text = "ACTION AT LIMIT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Mode 1: Warn Only
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enforcementMode == QuotaEnforcementMode.WARN_ONLY)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { enforcementMode = QuotaEnforcementMode.WARN_ONLY }
                        .testTag("enforcement_warn_only")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = enforcementMode == QuotaEnforcementMode.WARN_ONLY,
                            onClick = { enforcementMode = QuotaEnforcementMode.WARN_ONLY }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "⚠️ Warn Only",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Notify when reaching warning and limit. Traffic will NOT be interrupted.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode 2: Block at Limit
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enforcementMode == QuotaEnforcementMode.BLOCK_AT_LIMIT)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT }
                        .testTag("enforcement_block_at_limit")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = enforcementMode == QuotaEnforcementMode.BLOCK_AT_LIMIT,
                            onClick = { enforcementMode = QuotaEnforcementMode.BLOCK_AT_LIMIT }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "🛑 Block at Limit",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                            Text(
                                text = "Notify and restrict network access via DataGuard Firewall when quota is reached.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val num = limitInput.toLongOrNull() ?: 500L
                            val totalBytes = if (isGB) num * 1024L * 1024L * 1024L else num * 1024L * 1024L
                            onSave(
                                totalBytes,
                                warningThreshold.toInt(),
                                enforcementMode,
                                selectedCycle,
                                cycleStartDay,
                                customCycleDays,
                                isQuotaEnabled
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_quota_button")
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Quota")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconView(
    packageName: String,
    appName: String,
    isBlocked: Boolean
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = appName,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isBlocked) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
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
        title = { Text("Select Custom Range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose a historical inspection window for NetworkStatsManager telemetry:",
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
