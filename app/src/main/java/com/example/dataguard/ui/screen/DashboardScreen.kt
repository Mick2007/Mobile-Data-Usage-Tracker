package com.example.dataguard.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.DataGuardApp
import com.example.dataguard.data.entity.AppDataUsage
import com.example.dataguard.data.entity.BlockedTrafficEventEntity
import com.example.dataguard.data.entity.DomainSummary
import com.example.dataguard.data.entity.QuotaBreachEventEntity
import com.example.dataguard.ui.component.ComprehensiveAppDetailDialog
import com.example.dataguard.ui.component.ComprehensiveDomainDetailDialog
import com.example.dataguard.ui.component.NetworkTimelineGraph
import com.example.dataguard.ui.viewmodel.DashboardViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import com.example.dataguard.utils.DataFormatter
import com.example.dataguard.vpn.model.VpnStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = ViewModelFactory)
) {
    val context = LocalContext.current
    val latestUsage by viewModel.latestUsage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val hasUsageStats by viewModel.hasUsageStatsPermission.collectAsState()
    val isNetworkStatsActive by viewModel.isFromNetworkStats.collectAsState()
    val liveTxSpeed by viewModel.liveTxSpeed.collectAsState()
    val liveRxSpeed by viewModel.liveRxSpeed.collectAsState()
    val vpnState by viewModel.vpnState.collectAsState()

    // 1-5 Core Metrics
    val dailyMobileMB = viewModel.getTotalDailyMobileMB()
    val dailyWifiMB = viewModel.getTotalDailyWifiMB()
    val todayTotalMB = viewModel.getTodayTotalUsageMB()
    val billingCycleMB = viewModel.getBillingCycleUsageMB()
    val remainingAllowanceMB by viewModel.remainingAllowanceMB.collectAsState()
    val allowanceProgress by viewModel.allowanceProgress.collectAsState()

    // 6-7 Top Apps and Domains
    val topApps by viewModel.topApps.collectAsState()
    val topDomains by viewModel.topDomains.collectAsState()

    // 9-10 Recent Firewall Events and Alerts
    val recentBlockedEvents by viewModel.recentBlockedEvents.collectAsState()
    val recentAlerts by viewModel.recentAlerts.collectAsState()

    // Graph State
    val timelinePoints by viewModel.timelinePoints.collectAsState()
    val selectedPoint by viewModel.selectedPoint.collectAsState()
    val selectedIntervalDetails by viewModel.selectedIntervalDetails.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedDayMillis by viewModel.selectedDateMillis.collectAsState()

    // Detail Dialogs State
    val selectedAppForDetail by viewModel.selectedAppForDetail.collectAsState()
    val selectedAppQuota by viewModel.selectedAppQuota.collectAsState()
    val isAppBlocked by viewModel.isAppBlocked.collectAsState()
    val appContactedFlows by viewModel.appContactedFlows.collectAsState()

    val selectedDomainForDetail by viewModel.selectedDomainForDetail.collectAsState()
    val domainFlows by viewModel.domainFlows.collectAsState()
    val domainEvaluation by viewModel.domainEvaluation.collectAsState()

    var showCustomDateDialog by remember { mutableStateOf(false) }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn(context)
        } else {
            DataGuardApp.instance.vpnSessionManager.onPermissionDenied()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("dashboard_screen_scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header: Title, Telemetry Mode, and Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "DataGuard Shield",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "DataGuard",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = if (isNetworkStatsActive) "NetworkStatsManager Active" else "TrafficStats Fallback",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.loadData() },
                    modifier = Modifier.testTag("refresh_dashboard_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }

        // Notification / Message Banner
        AnimatedVisibility(visible = userMessage != null) {
            userMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { viewModel.clearMessage() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 11. Current VPN / Firewall State Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("current_vpn_firewall_state_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (vpnState.status) {
                    VpnStatus.ACTIVE -> Color(0xFFDCFCE7).copy(alpha = 0.7f)
                    VpnStatus.STARTING -> Color(0xFFFEF3C7).copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (vpnState.status) {
                                    VpnStatus.ACTIVE -> Color(0xFF166534)
                                    VpnStatus.STARTING -> Color(0xFFD97706)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (vpnState.status) {
                                VpnStatus.ACTIVE -> "VPN Firewall Protected"
                                VpnStatus.STARTING -> "Connecting VPN..."
                                else -> "Firewall Idle / Standby"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (vpnState.status) {
                                VpnStatus.ACTIVE -> Color(0xFF166534)
                                VpnStatus.STARTING -> Color(0xFFD97706)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = "Allowed: ${DataFormatter.formatBytesShort(vpnState.bytesAllowed)} • Blocked: ${DataFormatter.formatBytesShort(vpnState.bytesBlocked)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        if (vpnState.status == VpnStatus.ACTIVE) {
                            viewModel.stopVpn()
                        } else {
                            val prepIntent = DataGuardApp.instance.vpnSessionManager.getPrepareIntent(context)
                            if (prepIntent != null) {
                                vpnPrepareLauncher.launch(prepIntent)
                            } else {
                                viewModel.startVpn(context)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vpnState.status == VpnStatus.ACTIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("vpn_toggle_button")
                ) {
                    Icon(
                        imageVector = if (vpnState.status == VpnStatus.ACTIVE) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (vpnState.status == VpnStatus.ACTIVE) "Stop" else "Start")
                }
            }
        }

        // 8. Live Network Activity (Real-time speed pulse)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_network_activity_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Live Network Speed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time socket transmission",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = DataFormatter.formatSpeed(liveRxSpeed),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Text("Download", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = DataFormatter.formatSpeed(liveTxSpeed),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6)
                            )
                        }
                        Text("Upload", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 1-5. Core Usage Metrics Grid
        Text(
            text = "USAGE OVERVIEW",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Row 1: Current Mobile Usage & Current Wi-Fi Usage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UsageMetricCard(
                title = "Mobile Data",
                value = String.format(Locale.US, "%.1f MB", dailyMobileMB),
                subLabel = "Tx: ${DataFormatter.formatBytesShort(latestUsage?.totalMobileTx ?: 0L)} • Rx: ${DataFormatter.formatBytesShort(latestUsage?.totalMobileRx ?: 0L)}",
                icon = Icons.Default.SignalCellularAlt,
                iconColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_mobile_usage_card")
            )
            UsageMetricCard(
                title = "Wi-Fi Data",
                value = String.format(Locale.US, "%.1f MB", dailyWifiMB),
                subLabel = "Tx: ${DataFormatter.formatBytesShort(latestUsage?.totalWifiTx ?: 0L)} • Rx: ${DataFormatter.formatBytesShort(latestUsage?.totalWifiRx ?: 0L)}",
                icon = Icons.Default.Wifi,
                iconColor = Color(0xFF8B5CF6),
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_wifi_usage_card")
            )
        }

        // Row 2: Today's Usage & Current Billing-Cycle Usage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UsageMetricCard(
                title = "Today's Total",
                value = String.format(Locale.US, "%.1f MB", todayTotalMB),
                subLabel = "Combined Mobile & Wi-Fi",
                icon = Icons.Default.CalendarMonth,
                iconColor = Color(0xFF10B981),
                modifier = Modifier
                    .weight(1f)
                    .testTag("today_usage_card")
            )
            UsageMetricCard(
                title = "Billing Cycle",
                value = String.format(Locale.US, "%.1f MB", billingCycleMB),
                subLabel = "Current monthly billing cycle",
                icon = Icons.Default.Timeline,
                iconColor = Color(0xFFF59E0B),
                modifier = Modifier
                    .weight(1f)
                    .testTag("current_billing_cycle_usage_card")
            )
        }

        // Row 3: 5. Remaining Data Allowance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remaining_data_allowance_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Remaining Data Allowance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (remainingAllowanceMB != null) "${remainingAllowanceMB} MB left" else "No plan limit set",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if ((allowanceProgress) >= 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(allowanceProgress)
                            .height(8.dp)
                            .background(
                                when {
                                    allowanceProgress >= 0.9f -> MaterialTheme.colorScheme.error
                                    allowanceProgress >= 0.75f -> Color(0xFFD97706)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${String.format(Locale.US, "%.0f%%", allowanceProgress * 100)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Plan Limit: ${if (viewModel.dailyLimitMB > 0) "${viewModel.dailyLimitMB} MB (Daily)" else "${viewModel.monthlyLimitMB} MB (Monthly)"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Timeline Graph (Upload, Download, Total, Dropdown Selection, Date Navigation, and Interval Tapping Inspector)
        NetworkTimelineGraph(
            points = timelinePoints,
            selectedPoint = selectedPoint,
            selectedIntervalDetails = selectedIntervalDetails,
            selectedFilter = selectedFilter,
            selectedDayMillis = selectedDayMillis,
            onSelectFilter = { viewModel.setGraphFilter(it) },
            onPreviousDay = { viewModel.goToPreviousDay() },
            onNextDay = { viewModel.goToNextDay() },
            onSelectDay = { viewModel.setSelectedDate(it) },
            onIntervalTapped = { viewModel.selectIntervalPoint(it) },
            onAppTapped = { pkg, uid -> viewModel.openAppDetailByPackage(pkg, uid) },
            onDomainTapped = { domain -> viewModel.openDomainDetail(domain) }
        )

        // 6. Top Applications Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("top_applications_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top Applications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${topApps.size} apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (topApps.isEmpty()) {
                    Text(
                        text = "No applications observed communicating yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topApps.take(5).forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.openAppDetail(app) }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.appName.take(1).uppercase(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Cell: ${DataFormatter.formatBytesShort(app.mobileTotalBytes)} • Wi-Fi: ${DataFormatter.formatBytesShort(app.wifiTotalBytes)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = DataFormatter.formatBytes(app.totalBytes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "↑ ${DataFormatter.formatBytesShort(app.txBytes)} • ↓ ${DataFormatter.formatBytesShort(app.rxBytes)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Top Domains Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("top_domains_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top Domains Observed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${topDomains.size} domains",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (topDomains.isEmpty()) {
                    Text(
                        text = "No domain flows captured yet. Activate the VPN firewall to log hostnames.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topDomains.take(5).forEach { domain ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.openDomainDetail(domain.domain) }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = domain.domain,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (domain.associatedApps.isNotEmpty()) domain.associatedApps.joinToString(", ") else "Unknown app",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = DataFormatter.formatBytes(domain.totalBytes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${domain.connectionCount} conns",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 9. Recent Firewall Events Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_firewall_events_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Firewall Events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${recentBlockedEvents.size} events",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (recentBlockedEvents.isEmpty()) {
                    Text(
                        text = "No blocked traffic events recorded. All permitted connections are passing safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentBlockedEvents.take(4).forEach { event ->
                            val eventTime = remember(event.timestamp) {
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = event.appName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${event.reason} • $eventTime",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "BLOCKED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // 10. Recent Alerts Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_alerts_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${recentAlerts.size} alerts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (recentAlerts.isEmpty()) {
                    Text(
                        text = "No policy breaches or high-usage quota warnings logged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentAlerts.take(4).forEach { alert ->
                            val alertTime = remember(alert.timestamp) {
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (alert.eventType == "LIMIT_REACHED") MaterialTheme.colorScheme.error else Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = alert.appName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Used: ${DataFormatter.formatBytesShort(alert.usedBytes)} of ${DataFormatter.formatBytesShort(alert.limitBytes)} • $alertTime",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (alert.eventType == "LIMIT_REACHED") MaterialTheme.colorScheme.errorContainer else Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = alert.eventType.replace("_", " "),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (alert.eventType == "LIMIT_REACHED") MaterialTheme.colorScheme.error else Color(0xFFD97706),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // App Detail Dialog
    if (selectedAppForDetail != null) {
        ComprehensiveAppDetailDialog(
            app = selectedAppForDetail!!,
            quotaCalculation = selectedAppQuota,
            isBlocked = isAppBlocked,
            contactedFlows = appContactedFlows,
            onToggleFirewall = { viewModel.toggleAppFirewall(it) },
            onDomainClicked = { domain ->
                viewModel.closeAppDetail()
                viewModel.openDomainDetail(domain)
            },
            onDismiss = { viewModel.closeAppDetail() }
        )
    }

    // Domain Detail Dialog
    if (selectedDomainForDetail != null) {
        val domain = selectedDomainForDetail!!
        ComprehensiveDomainDetailDialog(
            domainName = domain.domain,
            totalBytes = domain.totalBytes,
            uploadBytes = domain.uploadBytes,
            downloadBytes = domain.downloadBytes,
            connectionCount = domain.connectionCount,
            firstSeen = domain.firstSeen,
            lastSeen = domain.lastSeen,
            associatedApps = domain.associatedApps,
            isBlocked = domainEvaluation?.decision == com.example.dataguard.vpn.engine.FirewallDecision.BLOCK,
            firewallReason = domainEvaluation?.reason,
            flows = domainFlows,
            onToggleFirewall = { viewModel.toggleDomainFirewall() },
            onAppClicked = { appName ->
                viewModel.closeDomainDetail()
                val matchedApp = topApps.find { it.appName == appName }
                if (matchedApp != null) {
                    viewModel.openAppDetail(matchedApp)
                }
            },
            onDismiss = { viewModel.closeDomainDetail() }
        )
    }

    // Custom Date Range Dialog
    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            initialStart = viewModel.customStartTime,
            initialEnd = viewModel.customEndTime,
            onDismiss = { showCustomDateDialog = false },
            onApply = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showCustomDateDialog = false
            }
        )
    }
}

@Composable
private fun UsageMetricCard(
    title: String,
    value: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomDateRangeDialog(
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    var daysBack by remember { mutableStateOf(7) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Date Window", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select custom time span to analyze:", style = MaterialTheme.typography.bodyMedium)
                listOf(3, 7, 14, 30, 60).forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { daysBack = days }
                            .background(if (daysBack == days) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Past $days days", fontWeight = if (daysBack == days) FontWeight.Bold else FontWeight.Normal)
                        if (daysBack == days) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val start = now - (daysBack.toLong() * 24 * 60 * 60 * 1000L)
                    onApply(start, now)
                }
            ) {
                Text("Apply Range")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
