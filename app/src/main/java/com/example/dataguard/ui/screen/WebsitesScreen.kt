package com.example.dataguard.ui.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dataguard.data.entity.AttributionConfidence
import com.example.dataguard.data.entity.DomainRuleEntity
import com.example.dataguard.data.entity.DomainSummary
import com.example.dataguard.data.entity.NetworkFlowEntity
import com.example.dataguard.ui.viewmodel.DomainFilterTab
import com.example.dataguard.ui.viewmodel.DomainSortOption
import com.example.dataguard.ui.viewmodel.DomainTimeWindow
import com.example.dataguard.ui.viewmodel.ViewModelFactory
import com.example.dataguard.ui.viewmodel.WebsitesViewModel
import com.example.dataguard.utils.DataFormatter
import com.example.dataguard.vpn.engine.FirewallDecision
import com.example.dataguard.vpn.engine.FirewallEvaluationResult
import com.example.dataguard.vpn.engine.NetworkInterfaceType
import com.example.dataguard.vpn.engine.RuleType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsitesScreen(
    viewModel: WebsitesViewModel = viewModel(factory = ViewModelFactory)
) {
    val topDomains by viewModel.topDomains.collectAsState()
    val selectedTimeWindow by viewModel.selectedTimeWindow.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val selectedFilterTab by viewModel.selectedFilterTab.collectAsState()
    val selectedDomainDetail by viewModel.selectedDomainDetail.collectAsState()
    val selectedDomainFlows by viewModel.selectedDomainFlows.collectAsState()
    val domainRules by viewModel.domainRules.collectAsState()

    var showAddDomainDialog by remember { mutableStateOf(false) }
    var showTechnicalDisclaimerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Screen Header with Add Domain and Info Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Websites & Domains",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "${topDomains.size} domains • Wildcard (*.) & App-level blocking",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Add Domain Rule Button
                Button(
                    onClick = { showAddDomainDialog = true },
                    modifier = Modifier.testTag("add_domain_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Domain", style = MaterialTheme.typography.labelSmall)
                }

                IconButton(
                    onClick = { showTechnicalDisclaimerDialog = true },
                    modifier = Modifier.testTag("websites_disclaimer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Technical Disclosure",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs: All Domains, Blocked, Allowed
        TabRow(
            selectedTabIndex = selectedFilterTab.ordinal,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            DomainFilterTab.values().forEach { tab ->
                val isSelected = selectedFilterTab == tab
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.setFilterTab(tab) },
                    text = {
                        Text(
                            text = tab.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search domains or apps (e.g. youtube.com, facebook)...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
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
                .testTag("websites_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Time Window & Sort Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DomainTimeWindow.values().forEach { window ->
                    val isSelected = selectedTimeWindow == window
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setTimeWindow(window) },
                        label = { Text(window.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("filter_window_${window.name.lowercase()}")
                    )
                }
            }

            // Sort Dropdown Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DomainSortOption.values().forEach { sort ->
                    val isSelected = sortOption == sort
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSortOption(sort) },
                        label = { Text(sort.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("sort_${sort.name.lowercase()}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Domains List
        if (topDomains.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No domains matching '$searchQuery'"
                        else "No ${selectedFilterTab.label.lowercase()} in this time window.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use 'Add Domain' to enforce a new wildcard/domain rule, or browse with VPN on.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { viewModel.testTrackWebsite("wikipedia.org") },
                        modifier = Modifier.testTag("test_track_website_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Website Traffic Tracking")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().testTag("top_domains_list")
            ) {
                items(topDomains, key = { it.domain }) { domainSummary ->
                    val eval = viewModel.getDomainStatus(
                        domain = domainSummary.domain,
                        packageName = domainSummary.associatedPackages.firstOrNull()
                    )
                    val isBlocked = eval.decision == FirewallDecision.BLOCK

                    DomainItemCard(
                        summary = domainSummary,
                        isBlocked = isBlocked,
                        evaluation = eval,
                        onClick = { viewModel.selectDomain(domainSummary) },
                        onToggleBlock = { viewModel.toggleDomainBlock(domainSummary.domain) }
                    )
                }
            }
        }
    }

    // Add Domain Rule Dialog
    if (showAddDomainDialog) {
        AddDomainRuleDialog(
            onDismiss = { showAddDomainDialog = false },
            onConfirmGlobal = { domainPattern, block, networkScope ->
                viewModel.addGlobalDomainRule(domainPattern, block, networkScope)
                showAddDomainDialog = false
            },
            onConfirmApp = { domainPattern, pkg, appName, block, networkScope ->
                viewModel.addAppDomainRule(domainPattern, pkg, appName, block, networkScope)
                showAddDomainDialog = false
            }
        )
    }

    // Domain Detail Dialog
    if (selectedDomainDetail != null) {
        val domain = selectedDomainDetail!!
        val eval = viewModel.getDomainStatus(
            domain = domain.domain,
            packageName = domain.associatedPackages.firstOrNull()
        )
        val rules = viewModel.getRulesForDomain(domain.domain)

        DomainDetailDialog(
            domain = domain,
            flows = selectedDomainFlows,
            evaluation = eval,
            rules = rules,
            onToggleBlock = { viewModel.toggleDomainBlock(domain.domain) },
            onDeleteRule = { viewModel.deleteRule(it) },
            onDismiss = { viewModel.clearSelectedDomain() }
        )
    }

    // Technical Disclosure / Disclaimer Dialog
    if (showTechnicalDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showTechnicalDisclaimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Domain Firewall & Attribution Reality")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Deterministic Rule Priority:\n1. APP + DOMAIN rule (highest priority)\n2. GLOBAL DOMAIN rule\n3. APP RULE (Application disallowed toggle)\n4. DEFAULT (ALLOW)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Wildcard & Subdomain Matching: Adding 'youtube.com' or '*.youtube.com' automatically matches 'm.youtube.com', 'video.youtube.com', and 'www.youtube.com' using domain label matching (not naive substring).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Network Scope Restrictions: Rules can be applied to ANY interface, MOBILE only, or WIFI only.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Zero HTTPS Decryption: DataGuard never decrypts HTTPS payloads or installs fake root certificates. Only network metadata (DNS, TLS SNI, IP) is inspected.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showTechnicalDisclaimerDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
private fun DomainItemCard(
    summary: DomainSummary,
    isBlocked: Boolean,
    evaluation: FirewallEvaluationResult,
    onClick: () -> Unit,
    onToggleBlock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("domain_card_${summary.domain}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
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
            // Header Row: Domain name, Confidence Badge & Block/Allow Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isBlocked) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Default.Block
                                else if (summary.domain.startsWith("Unknown / IP-only")) Icons.Default.Public
                                else Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isBlocked) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = summary.domain,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1
                            )
                            if (isBlocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BLOCKED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // Associated Apps preview
                        if (summary.associatedAppNames.isNotEmpty()) {
                            Text(
                                text = summary.associatedAppNames.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Confidence Chip & Fast Toggle Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConfidenceBadge(confidence = summary.confidence)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isBlocked) {
                        Button(
                            onClick = onToggleBlock,
                            modifier = Modifier.testTag("unblock_btn_${summary.domain}"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("ALLOW", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onToggleBlock,
                            modifier = Modifier.testTag("block_btn_${summary.domain}"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("BLOCK", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats Row: Total Data, Upload, Download, Connections
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Upload
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = DataFormatter.formatBytesShort(summary.uploadBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }

                    // Download
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = DataFormatter.formatBytesShort(summary.downloadBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }

                    // Connections count
                    Text(
                        text = "${summary.connectionCount} conns",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }

                // Total Data Highlight
                Text(
                    text = DataFormatter.formatBytes(summary.totalBytes),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: AttributionConfidence) {
    val (bgColor, textColor) = when (confidence) {
        AttributionConfidence.HIGH -> Pair(Color(0xFFDCFCE7), Color(0xFF166534))
        AttributionConfidence.MEDIUM -> Pair(Color(0xFFFEF3C7), Color(0xFF92400E))
        AttributionConfidence.LOW -> Pair(Color(0xFFFFEDD5), Color(0xFF9A3412))
        AttributionConfidence.UNKNOWN -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = confidence.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}

@Composable
private fun DomainDetailDialog(
    domain: DomainSummary,
    flows: List<NetworkFlowEntity>,
    evaluation: FirewallEvaluationResult,
    rules: List<DomainRuleEntity>,
    onToggleBlock: () -> Unit,
    onDeleteRule: (DomainRuleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val firstSeenFormatted = remember(domain.firstSeen) {
        val sdf = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
        sdf.format(Date(domain.firstSeen))
    }
    val lastSeenFormatted = remember(domain.lastSeen) {
        val sdf = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
        sdf.format(Date(domain.lastSeen))
    }

    val isBlocked = evaluation.decision == FirewallDecision.BLOCK

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Domain Telemetry",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    ConfidenceBadge(confidence = domain.confidence)
                }
                Text(
                    text = domain.domain,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Firewall Status & Reason Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else Color(0xFFDCFCE7).copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isBlocked) MaterialTheme.colorScheme.error else Color(0xFF166534),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBlocked) "STATUS: BLOCKED" else "STATUS: ALLOWED",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBlocked) MaterialTheme.colorScheme.error else Color(0xFF166534)
                                )
                            )
                        }
                        if (evaluation.reason != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reason: ${evaluation.reason}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                // Key metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Traffic", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DataFormatter.formatBytes(domain.totalBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Upload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DataFormatter.formatBytes(domain.uploadBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(DataFormatter.formatBytes(domain.downloadBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Associated Applications
                Text("Associated Applications:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                if (domain.associatedAppNames.isNotEmpty()) {
                    domain.associatedAppNames.forEachIndexed { idx, appName ->
                        val pkg = domain.associatedPackages.getOrElse(idx) { "" }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(6.dp)) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (pkg.isNotEmpty()) "$appName ($pkg)" else appName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                } else {
                    Text("No direct application mapping identified (System / Network routing)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Active Rules Table for this Domain
                if (rules.isNotEmpty()) {
                    Text("Configured Domain Rules:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    rules.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${rule.ruleType}: ${rule.domainPattern} [${rule.networkScope}]",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                if (rule.appName != null) {
                                    Text(
                                        text = "Scope: ${rule.appName} (${rule.packageName})",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteRule(rule) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                }

                // Observations details
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Connections:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${domain.connectionCount}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("First Seen:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(firstSeenFormatted, style = MaterialTheme.typography.labelSmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Last Seen:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lastSeenFormatted, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onToggleBlock()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("detail_toggle_block_btn")
                ) {
                    Text(if (isBlocked) "Allow Globally" else "Block Globally")
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("domain_detail_close_btn")) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun AddDomainRuleDialog(
    onDismiss: () -> Unit,
    onConfirmGlobal: (domainPattern: String, block: Boolean, networkScope: String) -> Unit,
    onConfirmApp: (domainPattern: String, pkg: String, appName: String, block: Boolean, networkScope: String) -> Unit
) {
    var domainPattern by remember { mutableStateOf("") }
    var isAppSpecific by remember { mutableStateOf(false) }
    var packageName by remember { mutableStateOf("com.android.chrome") }
    var appName by remember { mutableStateOf("Chrome") }
    var isBlockAction by remember { mutableStateOf(true) }
    var networkScope by remember { mutableStateOf("ANY") } // ANY, MOBILE, WIFI

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Domain Rule")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = domainPattern,
                    onValueChange = { domainPattern = it },
                    label = { Text("Domain / Pattern") },
                    placeholder = { Text("e.g. youtube.com, *.facebook.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_rule_domain_input")
                )

                Text(
                    text = "Subdomains (*.) and international domains (IDN) are normalized automatically.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )

                // Action Type: Block vs Allow
                Text("Action:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isBlockAction,
                            onClick = { isBlockAction = true },
                            modifier = Modifier.testTag("radio_action_block")
                        )
                        Text("BLOCK", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isBlockAction,
                            onClick = { isBlockAction = false },
                            modifier = Modifier.testTag("radio_action_allow")
                        )
                        Text("ALLOW", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF10B981)))
                    }
                }

                // Scope: Global vs App-Specific
                Text("Rule Scope:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isAppSpecific,
                            onClick = { isAppSpecific = false },
                            modifier = Modifier.testTag("radio_scope_global")
                        )
                        Text("Global (All Apps)", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isAppSpecific,
                            onClick = { isAppSpecific = true },
                            modifier = Modifier.testTag("radio_scope_app")
                        )
                        Text("App-Specific", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (isAppSpecific) {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            appName = when {
                                it.contains("chrome") -> "Chrome"
                                it.contains("youtube") -> "YouTube"
                                it.contains("instagram") -> "Instagram"
                                it.contains("whatsapp") -> "WhatsApp"
                                else -> it
                            }
                        },
                        label = { Text("Package Name") },
                        placeholder = { Text("e.g. com.android.chrome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_rule_package_input")
                    )
                }

                // Network Interface Scope: ANY, MOBILE, WIFI
                Text("Network Interface:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ANY", "MOBILE", "WIFI").forEach { scope ->
                        FilterChip(
                            selected = networkScope == scope,
                            onClick = { networkScope = scope },
                            label = { Text(scope, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("network_scope_$scope")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domainPattern.isNotBlank()) {
                        if (isAppSpecific && packageName.isNotBlank()) {
                            onConfirmApp(domainPattern, packageName, appName, isBlockAction, networkScope)
                        } else {
                            onConfirmGlobal(domainPattern, isBlockAction, networkScope)
                        }
                    }
                },
                enabled = domainPattern.isNotBlank(),
                modifier = Modifier.testTag("add_rule_confirm_btn")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
