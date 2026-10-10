package com.example.dataguard.ui.screen

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsCell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.dataguard.ui.component.CircularUsageGauge
import com.example.dataguard.ui.viewmodel.DashboardViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import com.example.dataguard.utils.DataFormatter
import com.example.dataguard.vpn.model.VpnStatus

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = ViewModelFactory)
) {
    val latestUsage by viewModel.latestUsage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val hasUsageStats by viewModel.hasUsageStatsPermission.collectAsState()
    val isNetworkStatsActive by viewModel.isFromNetworkStats.collectAsState()
    val liveTxSpeed by viewModel.liveTxSpeed.collectAsState()
    val liveRxSpeed by viewModel.liveRxSpeed.collectAsState()
    val vpnState by viewModel.vpnState.collectAsState()

    val context = LocalContext.current
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn(context)
        } else {
            DataGuardApp.instance.vpnSessionManager.onPermissionDenied()
        }
    }

    val dailyMobileMB = viewModel.getTotalDailyMobileMB()
    val dailyWifiMB = viewModel.getTotalDailyWifiMB()
    val weeklyMobileMB = viewModel.getTotalWeeklyUsageMB()
    val weeklyWifiMB = viewModel.getTotalWeeklyWifiMB()
    val monthlyMobileMB = viewModel.getTotalMonthlyUsageMB()
    val monthlyWifiMB = viewModel.getTotalMonthlyWifiMB()

    val dailyLimitMB = viewModel.dailyLimitMB
    val dailyLimitBytes = dailyLimitMB * 1024L * 1024L
    val dailyUsedBytes = (dailyMobileMB * 1024.0 * 1024.0).toLong()

    val percentDaily = if (dailyLimitMB > 0) ((dailyMobileMB / dailyLimitMB) * 100).toInt() else 0
    val isNearLimit = percentDaily >= 80

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Monitoring Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
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

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (hasUsageStats) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasUsageStats) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasUsageStats) "DUAL-NET ACTIVE" else "LIMITED ACCESS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (hasUsageStats) Color(0xFF10B981) else Color(0xFFD97706)
                        )
                    )
                }
            }
        }

        // Permission Notice Banner if Usage Access is missing
        if (!hasUsageStats) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permission_notice_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Usage Access Required",
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Usage Access Permission Required",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                        )
                        Text(
                            text = "Grant permission to unlock accurate Wi-Fi accounting and reboot-resilient historical queries.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF92400E))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { DataGuardApp.instance.permissionHelper.requestPackageUsageStatsPermission() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant", fontSize = 11.sp)
                    }
                }
            }
        }

        // Notification Banner
        AnimatedVisibility(visible = userMessage != null) {
            userMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_message_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                        IconButton(
                            onClick = { viewModel.clearMessage() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Firewall / VPN Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("firewall_vpn_status_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when (vpnState.status) {
                                        VpnStatus.ACTIVE -> Color(0xFF10B981).copy(alpha = 0.2f)
                                        VpnStatus.STARTING -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        VpnStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        VpnStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (vpnState.status) {
                                    VpnStatus.ACTIVE -> Icons.Default.Shield
                                    VpnStatus.STARTING -> Icons.Default.HourglassTop
                                    VpnStatus.ERROR -> Icons.Default.ErrorOutline
                                    VpnStatus.OFF -> Icons.Default.Security
                                },
                                contentDescription = "VPN Status Icon",
                                tint = when (vpnState.status) {
                                    VpnStatus.ACTIVE -> Color(0xFF10B981)
                                    VpnStatus.STARTING -> Color(0xFFF59E0B)
                                    VpnStatus.ERROR -> MaterialTheme.colorScheme.error
                                    VpnStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Local Firewall & VPN",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "On-device traffic inspection foundation",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (vpnState.status) {
                            VpnStatus.ACTIVE -> Color(0xFF10B981).copy(alpha = 0.15f)
                            VpnStatus.STARTING -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            VpnStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                            VpnStatus.OFF -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = vpnState.status.label,
                            modifier = Modifier
                                .testTag("vpn_status_badge")
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = when (vpnState.status) {
                                    VpnStatus.ACTIVE -> Color(0xFF047857)
                                    VpnStatus.STARTING -> Color(0xFFB45309)
                                    VpnStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                    VpnStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        )
                    }
                }

                // Educational / Status Explanation Text
                Text(
                    text = when (vpnState.status) {
                        VpnStatus.OFF ->
                            "DataGuard uses a 100% local Android VPN to inspect packet metadata, enforce app quota blocks, and count data. Your traffic is NOT routed to remote servers. Internet continues working normally."
                        VpnStatus.STARTING ->
                            "Establishing local tun interface and configuring safe routing rules..."
                        VpnStatus.ACTIVE ->
                            "Local VPN is active. Monitoring IP packets and quota enforcement on-device. Standard Internet traffic is preserved."
                        VpnStatus.ERROR ->
                            vpnState.errorMessage ?: "An error occurred while establishing the local VPN interface."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (vpnState.status == VpnStatus.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Active Metrics Row (when ACTIVE)
                if (vpnState.status == VpnStatus.ACTIVE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Virtual IP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = vpnState.localAddress ?: "10.1.10.2",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Packets Inspected",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = "${vpnState.packetsProcessed}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "VPN Traffic",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = DataFormatter.formatBytes(vpnState.bytesProcessed),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // Explicit Start / Stop Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vpnState.status == VpnStatus.ACTIVE || vpnState.status == VpnStatus.STARTING) {
                        Button(
                            onClick = { viewModel.stopVpn() },
                            modifier = Modifier.testTag("vpn_stop_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop VPN",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop VPN")
                        }
                    } else {
                        Button(
                            onClick = {
                                val prepareIntent = VpnService.prepare(context)
                                if (prepareIntent != null) {
                                    vpnPrepareLauncher.launch(prepareIntent)
                                } else {
                                    // Permission already granted
                                    viewModel.startVpn(context)
                                }
                            },
                            modifier = Modifier.testTag("vpn_start_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start VPN",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Local VPN")
                        }
                    }
                }
            }
        }

        // Alert Card if reaching daily threshold
        if (isNearLimit) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("high_usage_warning_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (percentDaily >= 100) MaterialTheme.colorScheme.errorContainer
                    else Color(0xFFFEF3C7)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = if (percentDaily >= 100) MaterialTheme.colorScheme.error else Color(0xFFB45309),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (percentDaily >= 100) "Daily Limit Reached ($percentDaily%)"
                            else "Approaching Daily Limit ($percentDaily%)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (percentDaily >= 100) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF78350F)
                            )
                        )
                        Text(
                            text = "Consider switching to Wi-Fi or pausing high-bandwidth background sync.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (percentDaily >= 100) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF92400E)
                            )
                        )
                    }
                }
            }
        }

        // Main Circular Gauge Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("main_gauge_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TODAY'S CELLULAR CONSUMPTION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                CircularUsageGauge(
                    usedBytes = dailyUsedBytes,
                    limitBytes = dailyLimitBytes
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dual Network Interface Summary Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NetworkBadge(
                        icon = Icons.Default.SignalCellularAlt,
                        label = "Mobile Data",
                        amount = String.format("%.1f MB", dailyMobileMB),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    NetworkBadge(
                        icon = Icons.Default.Wifi,
                        label = "Wi-Fi Data",
                        amount = String.format("%.1f MB", dailyWifiMB),
                        tint = Color(0xFF6366F1)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Instantaneous Speeds (TrafficStats)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedIndicator(
                        icon = Icons.Default.ArrowDownward,
                        label = "Live Download",
                        speed = liveRxSpeed,
                        tint = Color(0xFF10B981)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    SpeedIndicator(
                        icon = Icons.Default.ArrowUpward,
                        label = "Live Upload",
                        speed = liveTxSpeed,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Period Metrics Grid (Daily, Weekly, Monthly)
        Text(
            text = "Interface Quotas",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DualMetricCard(
                title = "Today",
                mobileMB = dailyMobileMB,
                wifiMB = dailyWifiMB,
                limitMB = dailyLimitMB.toDouble(),
                modifier = Modifier.weight(1f)
            )
            DualMetricCard(
                title = "Week",
                mobileMB = weeklyMobileMB,
                wifiMB = weeklyWifiMB,
                limitMB = viewModel.weeklyLimitMB.toDouble(),
                modifier = Modifier.weight(1f)
            )
            DualMetricCard(
                title = "Month",
                mobileMB = monthlyMobileMB,
                wifiMB = monthlyWifiMB,
                limitMB = viewModel.monthlyLimitMB.toDouble(),
                modifier = Modifier.weight(1f)
            )
        }

        // Quick Controls & Actions Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_controls_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsCell,
                        contentDescription = "Cellular Controls",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cellular Controls & Simulation",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleMobileData(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enable_data_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Enable")
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleMobileData(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disable_data_button")
                    ) {
                        Text("Disable")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.loadData() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("refresh_dashboard_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }

                    Button(
                        onClick = { viewModel.simulateLiveTraffic() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulate_burst_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Simulate", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Burst Test")
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun SpeedIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    speed: Long,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = DataFormatter.formatSpeed(speed),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun DualMetricCard(
    title: String,
    mobileMB: Double,
    wifiMB: Double,
    limitMB: Double,
    modifier: Modifier = Modifier
) {
    val percent = if (limitMB > 0) ((mobileMB / limitMB) * 100).toInt().coerceIn(0, 100) else 0
    val color = when {
        percent >= 90 -> Color(0xFFEF4444)
        percent >= 70 -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.testTag("metric_card_${title.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%.0f MB", mobileMB),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "Wi-Fi: ${String.format("%.0f MB", wifiMB)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF6366F1),
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent / 100f)
                        .height(4.dp)
                        .background(color)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$percent% plan limit",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
