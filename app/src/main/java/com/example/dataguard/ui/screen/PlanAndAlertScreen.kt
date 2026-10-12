package com.example.dataguard.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.data.entity.BillingCycleType
import com.example.dataguard.data.entity.DataPlanEntity
import com.example.dataguard.data.entity.NotificationAlertCategory
import com.example.dataguard.data.entity.NotificationHistoryEntity
import com.example.dataguard.data.entity.NotificationPreferencesEntity
import com.example.dataguard.data.entity.PlanTrafficScope
import com.example.dataguard.ui.viewmodel.PlanAndAlertViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import com.example.dataguard.utils.DataFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlanAndAlertScreen(
    viewModel: PlanAndAlertViewModel = viewModel(factory = ViewModelFactory)
) {
    val plan by viewModel.currentPlan.collectAsState()
    val planStatus by viewModel.planStatus.collectAsState()
    val preferences by viewModel.notificationPreferences.collectAsState()
    val historyList by viewModel.notificationHistory.collectAsState()
    val selectedCategory by viewModel.selectedHistoryCategory.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Data Plan, 1 = Alert Preferences, 2 = Notification History
    var showEditPlanDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Plans & Alerts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = plan.planName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { viewModel.refreshPlanStatus() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Plan")
                }
            }

            // Tabs: 0: Data Plan, 1: Alert Preferences, 2: History
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Data Plan") },
                    modifier = Modifier.testTag("tab_data_plan")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Alerts") },
                    modifier = Modifier.testTag("tab_alerts")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History (${historyList.size})") },
                    modifier = Modifier.testTag("tab_history")
                )
            }

            when (selectedTab) {
                0 -> DataPlanContent(
                    plan = plan,
                    status = planStatus,
                    onEditPlan = { showEditPlanDialog = true },
                    onApplyMtnPreset = { viewModel.applyPresetMtnMonthly() },
                    onApplyAirtelPreset = { viewModel.applyPresetAirtelWeekly() }
                )
                1 -> AlertPreferencesContent(
                    preferences = preferences,
                    onToggleMaster = { viewModel.setMasterNotifications(it) },
                    onToggleThreshold = { percent, enabled -> viewModel.toggleThresholdAlert(percent, enabled) },
                    onSetAppThreshold = { enabled, mb -> viewModel.setAppExceedsThreshold(enabled, mb) },
                    onSetDomainThreshold = { enabled, mb -> viewModel.setDomainExceedsThreshold(enabled, mb) },
                    onSetHotspotThreshold = { enabled, mb -> viewModel.setHotspotExceedsThreshold(enabled, mb) },
                    onSetSpike = { enabled, kbps -> viewModel.setSpikeAlert(enabled, kbps) },
                    onSetNewApp = { viewModel.setNewAppAlert(it) },
                    onSetNewDomain = { viewModel.setNewDomainAlert(it) },
                    onSetFirewallBlock = { viewModel.setFirewallBlockAlert(it) }
                )
                2 -> NotificationHistoryContent(
                    history = historyList,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { viewModel.setHistoryCategoryFilter(it) },
                    onClearHistory = { viewModel.clearNotificationHistory() },
                    onMarkAllRead = { viewModel.markAllNotificationsRead() }
                )
            }
        }
    }

    if (showEditPlanDialog) {
        EditDataPlanDialog(
            currentPlan = plan,
            onDismiss = { showEditPlanDialog = false },
            onSave = { name, allowanceGB, cycle, scope, startDay, customDays, rolloverGB, thresholds, excluded ->
                viewModel.savePlan(
                    name = name,
                    allowanceGB = allowanceGB,
                    cycleType = cycle,
                    trafficScope = scope,
                    cycleStartDay = startDay,
                    customDays = customDays,
                    rolloverGB = rolloverGB,
                    warningThresholds = thresholds,
                    excludedPackages = excluded
                )
                showEditPlanDialog = false
            }
        )
    }
}

@Composable
private fun DataPlanContent(
    plan: DataPlanEntity,
    status: com.example.dataguard.domain.plan.DataPlanStatus?,
    onEditPlan: () -> Unit,
    onApplyMtnPreset: () -> Unit,
    onApplyAirtelPreset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Plan Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_plan_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = plan.planName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${plan.billingCycle.replace('_', ' ')} • ${plan.trafficScope.replace('_', ' ')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onEditPlan,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("edit_plan_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Plan")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val totalAllowanceGB = plan.totalEffectiveAllowanceBytes / (1024.0 * 1024.0 * 1024.0)
                val usedBytes = status?.usedBytes ?: 0L
                val remainingBytes = status?.remainingBytes ?: plan.totalEffectiveAllowanceBytes
                val percentage = status?.usagePercentage ?: 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = DataFormatter.formatBytes(usedBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (percentage >= 90f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text("Used so far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = DataFormatter.formatBytes(remainingBytes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Remaining allowance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percentage / 100f)
                            .height(10.dp)
                            .background(
                                when {
                                    percentage >= 100f -> MaterialTheme.colorScheme.error
                                    percentage >= 90f -> Color(0xFFEF4444)
                                    percentage >= 75f -> Color(0xFFF59E0B)
                                    percentage >= 50f -> Color(0xFF3B82F6)
                                    else -> Color(0xFF10B981)
                                }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Allowance: ${String.format(Locale.US, "%.1f GB", totalAllowanceGB)}${if (plan.rolloverBytes > 0) " (+${DataFormatter.formatBytes(plan.rolloverBytes)} rollover)" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", percentage)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (status != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cycle Resets: ${status.cycleWindow.formattedResetDate} (${status.cycleWindow.daysRemaining} days left)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Warning Thresholds Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configured Warning Thresholds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(50, 75, 90, 100).forEach { th ->
                        val isConfigured = plan.warningThresholdsList.contains(th)
                        val isReached = (status?.usagePercentage ?: 0f) >= th

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isReached -> MaterialTheme.colorScheme.errorContainer
                                isConfigured -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$th%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isReached -> MaterialTheme.colorScheme.error
                                        isConfigured -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = if (isReached) "Triggered" else if (isConfigured) "Armed" else "Off",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Excluded Apps Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Excluded Apps from Plan Allowance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Traffic consumed by these apps will not deduct from your carrier quota (e.g. zero-rated services).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val excluded = plan.excludedPackagesList
                if (excluded.isEmpty()) {
                    Text(
                        text = "None. All applications count toward data allowance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        excluded.forEach { pkg ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = pkg.substringAfterLast("."),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Preset Configurations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Carrier Presets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quickly apply common network configurations:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onApplyMtnPreset,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_mtn_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MTN Monthly", fontWeight = FontWeight.Bold)
                            Text("10 GB • Mobile", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Button(
                        onClick = onApplyAirtelPreset,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_airtel_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Airtel Weekly", fontWeight = FontWeight.Bold)
                            Text("5 GB • Mobile", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertPreferencesContent(
    preferences: NotificationPreferencesEntity,
    onToggleMaster: (Boolean) -> Unit,
    onToggleThreshold: (Int, Boolean) -> Unit,
    onSetAppThreshold: (Boolean, Long) -> Unit,
    onSetDomainThreshold: (Boolean, Long) -> Unit,
    onSetHotspotThreshold: (Boolean, Long) -> Unit,
    onSetSpike: (Boolean, Long) -> Unit,
    onSetNewApp: (Boolean) -> Unit,
    onSetNewDomain: (Boolean) -> Unit,
    onSetFirewallBlock: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Alert Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (preferences.isEnabledMaster) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Enable Real-Time Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Actionable, non-spam notifications for usage events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = preferences.isEnabledMaster,
                    onCheckedChange = onToggleMaster,
                    modifier = Modifier.testTag("master_notifications_switch")
                )
            }
        }

        // Plan Thresholds (50%, 75%, 90%, 100%)
        Text(
            text = "PLAN USAGE THRESHOLDS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertToggleRow(
                    title = "50% Allowance Notice",
                    description = "Gentle notice when halfway through data quota",
                    checked = preferences.alertOn50Percent,
                    onCheckedChange = { onToggleThreshold(50, it) },
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "75% Warning Alert",
                    description = "Advises reviewing active streaming and downloads",
                    checked = preferences.alertOn75Percent,
                    onCheckedChange = { onToggleThreshold(75, it) },
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "90% Critical Warning",
                    description = "High-priority warning approaching plan expiration",
                    checked = preferences.alertOn90Percent,
                    onCheckedChange = { onToggleThreshold(90, it) },
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "100% Plan Exhausted Alert",
                    description = "Immediate action banner when 100% quota is breached",
                    checked = preferences.alertOn100Percent,
                    onCheckedChange = { onToggleThreshold(100, it) },
                    enabled = preferences.isEnabledMaster
                )
            }
        }

        // Specialized Activity Alerts
        Text(
            text = "ACTIVITY & SPIKE DETECTION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertToggleRow(
                    title = "App Exceeds Threshold (${preferences.appExceedsThresholdMb} MB)",
                    description = "Alerts if any app rapidly transfers large data",
                    checked = preferences.alertAppExceedsMb,
                    onCheckedChange = { onSetAppThreshold(it, preferences.appExceedsThresholdMb) },
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "Domain Exceeds Threshold (${preferences.domainExceedsThresholdMb} MB)",
                    description = "Alerts if any single host receives heavy data",
                    checked = preferences.alertDomainExceedsMb,
                    onCheckedChange = { onSetDomainThreshold(it, preferences.domainExceedsThresholdMb) },
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "Hotspot Exceeds Threshold (${preferences.hotspotExceedsThresholdMb} MB)",
                    description = "Protects against uncontrolled tethering data consumption",
                    checked = preferences.alertHotspotExceedsMb,
                    onCheckedChange = { onSetHotspotThreshold(it, preferences.hotspotExceedsThresholdMb) },
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "Unusual Speed Spike Alert",
                    description = "Flags sudden bursts above 2 MB/s",
                    checked = preferences.alertUnusualSpike,
                    onCheckedChange = { onSetSpike(it, preferences.spikeSpeedThresholdKbps) },
                    enabled = preferences.isEnabledMaster
                )
            }
        }

        // Security & Discovery Alerts
        Text(
            text = "SECURITY & DISCOVERY ALERTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertToggleRow(
                    title = "Newly Observed App Network Activity",
                    description = "Notify when an application connects for the first time",
                    checked = preferences.alertNewlyObservedApp,
                    onCheckedChange = onSetNewApp,
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "Newly Observed Domain Contacted",
                    description = "Notify when connections to unknown domains occur",
                    checked = preferences.alertNewlyObservedDomain,
                    onCheckedChange = onSetNewDomain,
                    enabled = preferences.isEnabledMaster
                )
                Divider()
                AlertToggleRow(
                    title = "Firewall Block Event",
                    description = "Notify when firewall successfully drops a connection",
                    checked = preferences.alertFirewallBlockEvent,
                    onCheckedChange = onSetFirewallBlock,
                    enabled = preferences.isEnabledMaster
                )
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun NotificationHistoryContent(
    history: List<NotificationHistoryEntity>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    val categories = listOf("ALL") + NotificationAlertCategory.values().map { it.name }
    val filtered = if (selectedCategory == "ALL") history else history.filter { it.category == selectedCategory }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alert Audit Log (${filtered.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                TextButton(onClick = onMarkAllRead) {
                    Text("Mark Read")
                }
                TextButton(onClick = onClearHistory) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == "ALL",
                onClick = { onSelectCategory("ALL") },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedCategory == NotificationAlertCategory.PLAN_THRESHOLD.name,
                onClick = { onSelectCategory(NotificationAlertCategory.PLAN_THRESHOLD.name) },
                label = { Text("Plan Limits") }
            )
            FilterChip(
                selected = selectedCategory == NotificationAlertCategory.FIREWALL_BLOCK.name,
                onClick = { onSelectCategory(NotificationAlertCategory.FIREWALL_BLOCK.name) },
                label = { Text("Firewall") }
            )
            FilterChip(
                selected = selectedCategory == NotificationAlertCategory.DATA_SPIKE.name,
                onClick = { onSelectCategory(NotificationAlertCategory.DATA_SPIKE.name) },
                label = { Text("Spikes") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No alerts in this category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filtered.forEach { item ->
                    val timeStr = remember(item.timestamp) {
                        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!item.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (item.category) {
                                            NotificationAlertCategory.PLAN_THRESHOLD.name -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                            NotificationAlertCategory.FIREWALL_BLOCK.name -> Color(0xFFDC2626).copy(alpha = 0.2f)
                                            NotificationAlertCategory.DATA_SPIKE.name -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (item.category) {
                                        NotificationAlertCategory.PLAN_THRESHOLD.name -> Icons.Default.SignalCellularAlt
                                        NotificationAlertCategory.FIREWALL_BLOCK.name -> Icons.Default.Block
                                        NotificationAlertCategory.DATA_SPIKE.name -> Icons.Default.Speed
                                        NotificationAlertCategory.HOTSPOT_EXCEEDS_QUOTA.name -> Icons.Default.WifiTethering
                                        else -> Icons.Default.Notifications
                                    },
                                    contentDescription = null,
                                    tint = when (item.category) {
                                        NotificationAlertCategory.PLAN_THRESHOLD.name -> Color(0xFFEF4444)
                                        NotificationAlertCategory.FIREWALL_BLOCK.name -> Color(0xFFDC2626)
                                        NotificationAlertCategory.DATA_SPIKE.name -> Color(0xFFF59E0B)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditDataPlanDialog(
    currentPlan: DataPlanEntity,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        allowanceGB: Double,
        cycle: BillingCycleType,
        scope: PlanTrafficScope,
        startDay: Int,
        customDays: Int,
        rolloverGB: Double,
        thresholds: List<Int>,
        excluded: List<String>
    ) -> Unit
) {
    var name by remember { mutableStateOf(currentPlan.planName) }
    var allowanceGbText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentPlan.totalAllowanceBytes / (1024.0 * 1024.0 * 1024.0)))
    }
    var rolloverGbText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", currentPlan.rolloverBytes / (1024.0 * 1024.0 * 1024.0)))
    }
    var cycleType by remember {
        mutableStateOf(try { BillingCycleType.valueOf(currentPlan.billingCycle) } catch (_: Exception) { BillingCycleType.MONTHLY })
    }
    var trafficScope by remember {
        mutableStateOf(try { PlanTrafficScope.valueOf(currentPlan.trafficScope) } catch (_: Exception) { PlanTrafficScope.MOBILE_ONLY })
    }
    var startDayText by remember { mutableStateOf(currentPlan.cycleStartDayOfMonth.toString()) }
    var customDaysText by remember { mutableStateOf(currentPlan.customCycleDays.toString()) }
    var excludedAppsText by remember { mutableStateOf(currentPlan.excludedPackageNamesJson) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure Data Plan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan Name (e.g. MTN Monthly)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = allowanceGbText,
                        onValueChange = { allowanceGbText = it },
                        label = { Text("Total Allowance (GB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rolloverGbText,
                        onValueChange = { rolloverGbText = it },
                        label = { Text("Rollover (GB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Billing Cycle
                Text("Billing Cycle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BillingCycleType.values().forEach { cycle ->
                        FilterChip(
                            selected = cycleType == cycle,
                            onClick = { cycleType = cycle },
                            label = { Text(cycle.label, fontSize = 11.sp) }
                        )
                    }
                }

                if (cycleType == BillingCycleType.MONTHLY) {
                    OutlinedTextField(
                        value = startDayText,
                        onValueChange = { startDayText = it },
                        label = { Text("Cycle Start Day of Month (1-28)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (cycleType == BillingCycleType.CUSTOM_DAYS) {
                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = { customDaysText = it },
                        label = { Text("Cycle Duration (Days)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Traffic Scope
                Text("Traffic Scope", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanTrafficScope.values().forEach { scope ->
                        FilterChip(
                            selected = trafficScope == scope,
                            onClick = { trafficScope = scope },
                            label = { Text(scope.label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = excludedAppsText,
                    onValueChange = { excludedAppsText = it },
                    label = { Text("Optional Excluded Apps (comma-separated packages)") },
                    placeholder = { Text("e.g. com.whatsapp, com.opera.mini") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val allowGb = allowanceGbText.toDoubleOrNull() ?: 10.0
                            val rollGb = rolloverGbText.toDoubleOrNull() ?: 0.0
                            val sDay = startDayText.toIntOrNull() ?: 1
                            val cDays = customDaysText.toIntOrNull() ?: 30
                            val excludedList = excludedAppsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                            onSave(
                                name,
                                allowGb,
                                cycleType,
                                trafficScope,
                                sDay,
                                cDays,
                                rollGb,
                                listOf(50, 75, 90, 100),
                                excludedList
                            )
                        }
                    ) {
                        Text("Save Plan")
                    }
                }
            }
        }
    }
}
