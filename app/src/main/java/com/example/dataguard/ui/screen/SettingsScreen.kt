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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import com.example.dataguard.utils.DataLimitUnit
import com.example.dataguard.utils.DataUnitUtils
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.ui.viewmodel.SettingsViewModel
import com.example.dataguard.ui.viewmodel.ViewModelFactory

@Composable
fun SettingsScreen(
    onNavigateToPlans: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = ViewModelFactory)
) {
    val hasUsageStats by viewModel.hasUsageStatsPermission.collectAsState()
    val hasBatteryExemption by viewModel.hasBatteryOptimizationExemption.collectAsState()
    val hasNotifications by viewModel.hasNotificationPermission.collectAsState()

    val dailyLimitBytes by viewModel.dailyLimitBytes.collectAsState()
    val weeklyLimitBytes by viewModel.weeklyLimitBytes.collectAsState()
    val monthlyLimitBytes by viewModel.monthlyLimitBytes.collectAsState()

    val dailyUnit by viewModel.dailyUnit.collectAsState()
    val weeklyUnit by viewModel.weeklyUnit.collectAsState()
    val monthlyUnit by viewModel.monthlyUnit.collectAsState()

    val dailyInputText by viewModel.dailyInputText.collectAsState()
    val weeklyInputText by viewModel.weeklyInputText.collectAsState()
    val monthlyInputText by viewModel.monthlyInputText.collectAsState()

    val dailyInputError by viewModel.dailyInputError.collectAsState()
    val weeklyInputError by viewModel.weeklyInputError.collectAsState()
    val monthlyInputError by viewModel.monthlyInputError.collectAsState()

    val isAutoIdleSaver by viewModel.isAutoIdleSaverEnabled.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Control Center",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "Configure quotas, alert thresholds, and security permissions",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Permissions Section
            Text(
                text = "System Permissions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            PermissionStatusItem(
                title = "Usage Access",
                description = "Required to read per-app bandwidth usage telemetry",
                icon = Icons.Default.Security,
                isGranted = hasUsageStats,
                onRequest = { viewModel.requestUsageStatsPermission() },
                testTag = "perm_usage_stats"
            )

            PermissionStatusItem(
                title = "Battery Optimization Exemption",
                description = "Prevents Android OS from stopping background tracking",
                icon = Icons.Default.BatteryAlert,
                isGranted = hasBatteryExemption,
                onRequest = { viewModel.requestBatteryOptimizationExemption() },
                testTag = "perm_battery"
            )

            PermissionStatusItem(
                title = "Notification Alerts",
                description = "Required to send real-time data limit and idle warnings",
                icon = Icons.Default.Notifications,
                isGranted = hasNotifications,
                onRequest = { viewModel.requestNotificationPermission() },
                testTag = "perm_notifications"
            )

            // Milestone 10: Carrier Data Plan & Alerts Navigation Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPlans() }
                    .testTag("nav_to_plans_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Carrier Data Plan & Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configure carrier quotas, rollover, thresholds & alert history",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToPlans,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("open_plans_button")
                    ) {
                        Text("Manage")
                    }
                }
            }

            // Data Limits Section
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Quick Sliders & Automation Limits",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            LimitInputFieldWithUnit(
                title = "Daily Limit",
                valueText = dailyInputText,
                unit = dailyUnit,
                errorMessage = dailyInputError,
                bytesEquivalent = dailyLimitBytes,
                onValueChange = { viewModel.updateDailyLimit(it) },
                onUnitChange = { viewModel.changeDailyUnit(it) },
                onUsePreviousLimit = { viewModel.applyPreviousDayLimit() },
                inputTestTag = "daily_limit_input",
                unitDropdownTestTag = "daily_limit_unit_dropdown",
                errorTestTag = "daily_limit_error",
                cardTestTag = "daily_quota_slider"
            )

            LimitInputFieldWithUnit(
                title = "Weekly Limit",
                valueText = weeklyInputText,
                unit = weeklyUnit,
                errorMessage = weeklyInputError,
                bytesEquivalent = weeklyLimitBytes,
                onValueChange = { viewModel.updateWeeklyLimit(it) },
                onUnitChange = { viewModel.changeWeeklyUnit(it) },
                inputTestTag = "weekly_limit_input",
                unitDropdownTestTag = "weekly_limit_unit_dropdown",
                errorTestTag = "weekly_limit_error",
                cardTestTag = "weekly_quota_slider"
            )

            LimitInputFieldWithUnit(
                title = "Monthly Limit",
                valueText = monthlyInputText,
                unit = monthlyUnit,
                errorMessage = monthlyInputError,
                bytesEquivalent = monthlyLimitBytes,
                onValueChange = { viewModel.updateMonthlyLimit(it) },
                onUnitChange = { viewModel.changeMonthlyUnit(it) },
                inputTestTag = "monthly_limit_input",
                unitDropdownTestTag = "monthly_limit_unit_dropdown",
                errorTestTag = "monthly_limit_error",
                cardTestTag = "monthly_quota_slider"
            )

            // Automation Section
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Automated Rules",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_idle_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Idle Saver",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto Idle Saver",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detects when phone is inactive for >5 min and alerts to pause non-essential cellular traffic",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Switch(
                        checked = isAutoIdleSaver,
                        onCheckedChange = { viewModel.setAutoIdleSaver(it) },
                        modifier = Modifier.testTag("auto_idle_switch")
                    )
                }
            }

            // Danger Zone Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_data_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Usage Statistics",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clears all local room records, toggle logs, and telemetry history.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_stats_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset All Data")
                    }
                }
            }

            // About DataGuard Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About DataGuard",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "DataGuard v1.0 • Android Jetpack Compose & Room Database architecture. Real-time carrier traffic analysis with zero background bloat.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset All Statistics?") },
                text = { Text("Are you sure you want to clear all data logs and history? This action cannot be reversed.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetAllStats()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Confirm Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionStatusItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onRequest: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        .background(
                            if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                        contentDescription = title,
                        tint = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isGranted) {
                OutlinedButton(
                    onClick = onRequest,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enable", fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun LimitInputFieldWithUnit(
    title: String,
    valueText: String,
    unit: DataLimitUnit,
    errorMessage: String?,
    bytesEquivalent: Long,
    onValueChange: (String) -> Unit,
    onUnitChange: (DataLimitUnit) -> Unit,
    inputTestTag: String,
    unitDropdownTestTag: String,
    errorTestTag: String,
    cardTestTag: String,
    onUsePreviousLimit: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTestTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (errorMessage == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Auto-saved",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Auto-saved",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Numeric Input Field
                OutlinedTextField(
                    value = valueText,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(inputTestTag),
                    singleLine = true,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                // Compact Unit Selector Dropdown
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag(unitDropdownTestTag),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = unit.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select unit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DataLimitUnit.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (option == unit) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == unit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onUnitChange(option)
                                },
                                modifier = Modifier.testTag("${inputTestTag}_unit_${option.name.lowercase(Locale.US)}")
                            )
                        }
                    }
                }
            }

            // Error display or equivalence helper
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.testTag(errorTestTag)
                )
            } else if (bytesEquivalent > 0) {
                val formattedEquivalent = when (unit) {
                    DataLimitUnit.MB -> {
                        val gbVal = DataUnitUtils.bytesToLimitValue(bytesEquivalent, DataLimitUnit.GB)
                        "Equivalent: ${DataUnitUtils.formatLimitValue(gbVal)} GB • ${java.text.NumberFormat.getIntegerInstance(Locale.US).format(bytesEquivalent)} bytes"
                    }
                    DataLimitUnit.GB -> {
                        val mbVal = DataUnitUtils.bytesToLimitValue(bytesEquivalent, DataLimitUnit.MB)
                        "Equivalent: ${DataUnitUtils.formatLimitValue(mbVal)} MB • ${java.text.NumberFormat.getIntegerInstance(Locale.US).format(bytesEquivalent)} bytes"
                    }
                }
                Text(
                    text = formattedEquivalent,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                )
            }

            if (onUsePreviousLimit != null && (valueText.isBlank() || errorMessage != null)) {
                Text(
                    text = "Tap to restore previous day's limit",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .clickable { onUsePreviousLimit() }
                        .padding(top = 2.dp)
                        .testTag("use_previous_day_limit_button")
                )
            }
        }
    }
}

@Composable
private fun QuotaCard(
    title: String,
    valueMB: Long,
    minMB: Float,
    maxMB: Float,
    stepMB: Float,
    onValueChange: (Long) -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (valueMB >= 1024) String.format("%.1f GB", valueMB / 1024.0) else "$valueMB MB",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = valueMB.toFloat(),
                onValueChange = { onValueChange(it.toLong()) },
                valueRange = minMB..maxMB,
                steps = ((maxMB - minMB) / stepMB).toInt() - 1
            )
        }
    }
}
