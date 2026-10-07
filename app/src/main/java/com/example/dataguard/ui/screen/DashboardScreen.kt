package com.example.dataguard.ui.screen

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsCell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.ui.component.CircularUsageGauge
import com.example.dataguard.ui.viewmodel.DashboardViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import com.example.dataguard.utils.DataFormatter

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = ViewModelFactory)
) {
    val latestUsage by viewModel.latestUsage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val liveTxSpeed by viewModel.liveTxSpeed.collectAsState()
    val liveRxSpeed by viewModel.liveRxSpeed.collectAsState()

    val dailyUsageMB = viewModel.getTotalDailyUsageMB()
    val weeklyUsageMB = viewModel.getTotalWeeklyUsageMB()
    val monthlyUsageMB = viewModel.getTotalMonthlyUsageMB()

    val dailyLimitMB = viewModel.dailyLimitMB
    val dailyLimitBytes = dailyLimitMB * 1024L * 1024L
    val dailyUsedBytes = (dailyUsageMB * 1024.0 * 1024.0).toLong()

    val percentDaily = if (dailyLimitMB > 0) ((dailyUsageMB / dailyLimitMB) * 100).toInt() else 0
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
                        text = "Real-time Mobile Monitor",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10B981).copy(alpha = 0.15f),
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
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE SHIELD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    )
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
                            text = "Consider restricting high-bandwidth video or background sync apps.",
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
                    text = "TODAY'S USAGE",
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

                Spacer(modifier = Modifier.height(20.dp))

                // Real-time Bandwidth Meters (Live upload & download speeds)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedIndicator(
                        icon = Icons.Default.ArrowDownward,
                        label = "Download",
                        speed = liveRxSpeed,
                        tint = Color(0xFF10B981)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    SpeedIndicator(
                        icon = Icons.Default.ArrowUpward,
                        label = "Upload",
                        speed = liveTxSpeed,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Period Metrics Grid (Daily, Weekly, Monthly)
        Text(
            text = "Usage Breakdown",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Today",
                valueMB = dailyUsageMB,
                limitMB = dailyLimitMB.toDouble(),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Week",
                valueMB = weeklyUsageMB,
                limitMB = viewModel.weeklyLimitMB.toDouble(),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Month",
                valueMB = monthlyUsageMB,
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
                        text = "Cellular Data Controls",
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
                        Text("Simulate")
                    }
                }
            }
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
private fun MetricCard(
    title: String,
    valueMB: Double,
    limitMB: Double,
    modifier: Modifier = Modifier
) {
    val percent = if (limitMB > 0) ((valueMB / limitMB) * 100).toInt().coerceIn(0, 100) else 0
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
                text = if (valueMB >= 1024) String.format("%.2f GB", valueMB / 1024) else String.format("%.0f MB", valueMB),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                text = "$percent% of limit",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
