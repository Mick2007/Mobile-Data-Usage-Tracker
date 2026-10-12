package com.example.dataguard.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dataguard.domain.model.AppIntervalUsage
import com.example.dataguard.domain.model.DashboardTimeRange
import com.example.dataguard.domain.model.DomainIntervalUsage
import com.example.dataguard.domain.model.GraphFilter
import com.example.dataguard.domain.model.GraphIntervalDetails
import com.example.dataguard.domain.model.NetworkTimelinePoint
import com.example.dataguard.utils.DataFormatter

/**
 * GlassWire-inspired analytical network activity timeline graph.
 *
 * Designed with exceptional readability:
 * - Clear distinct color coding for upload (blue), download (green), total.
 * - Supports filtering by: All, Mobile, Wi-Fi, Apps, Websites.
 * - Supports time ranges: Today, 7 days, 30 days, Custom.
 * - Interactive interval tapping: inspects exact apps, domains, allowed vs blocked traffic for that interval.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkTimelineGraph(
    points: List<NetworkTimelinePoint>,
    selectedPoint: NetworkTimelinePoint?,
    selectedIntervalDetails: GraphIntervalDetails?,
    selectedFilter: GraphFilter,
    selectedTimeRange: DashboardTimeRange,
    onSelectFilter: (GraphFilter) -> Unit,
    onSelectTimeRange: (DashboardTimeRange) -> Unit,
    onIntervalTapped: (NetworkTimelinePoint) -> Unit,
    onAppTapped: (String, Int) -> Unit,
    onDomainTapped: (String) -> Unit,
    onOpenCustomDateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("network_timeline_graph_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Time Range Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Network Activity Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap any interval to inspect active apps & domains",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time Range Selector Chips: Today, 7 Days, 30 Days, Custom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardTimeRange.values().forEach { range ->
                    val isSelected = selectedTimeRange == range
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable {
                                if (range == DashboardTimeRange.CUSTOM) {
                                    onOpenCustomDateDialog()
                                } else {
                                    onSelectTimeRange(range)
                                }
                            }
                            .testTag("time_range_chip_${range.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (range == DashboardTimeRange.CUSTOM) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips: All, Mobile, Wi-Fi, Apps, Websites
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GraphFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (filter) {
                                        GraphFilter.ALL -> Icons.Default.FilterList
                                        GraphFilter.MOBILE -> Icons.Default.PhoneAndroid
                                        GraphFilter.WIFI -> Icons.Default.Wifi
                                        GraphFilter.APPS -> Icons.Default.Security
                                        GraphFilter.WEBSITES -> Icons.Default.Language
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(filter.label)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("graph_filter_${filter.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Graph Metrics Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(label = "Download", color = Color(0xFF10B981))
                    LegendItem(label = "Upload", color = Color(0xFF3B82F6))
                    LegendItem(label = "Total", color = MaterialTheme.colorScheme.primary)
                }

                if (selectedPoint != null) {
                    Text(
                        text = "Selected: ${selectedPoint.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timeline Canvas Area
            TimelineGraphCanvas(
                points = points,
                selectedPoint = selectedPoint,
                filter = selectedFilter,
                onTapPoint = onIntervalTapped
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tapped Interval Inspector (Detailed breakdown for the selected timeline segment)
            if (selectedIntervalDetails != null) {
                IntervalTelemetryInspector(
                    details = selectedIntervalDetails,
                    onAppTapped = onAppTapped,
                    onDomainTapped = onDomainTapped
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Tap any column above to inspect what apps and domains transmitted data during that window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * High-performance Canvas rendering of the timeline graph.
 */
@Composable
private fun TimelineGraphCanvas(
    points: List<NetworkTimelinePoint>,
    selectedPoint: NetworkTimelinePoint?,
    filter: GraphFilter,
    onTapPoint: (NetworkTimelinePoint) -> Unit
) {
    val downloadColor = Color(0xFF10B981)
    val uploadColor = Color(0xFF3B82F6)
    val selectionOverlayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val selectionBorderColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val maxBytes = remember(points, filter) {
        val maxVal = points.maxOfOrNull { pt ->
            when (filter) {
                GraphFilter.MOBILE -> pt.mobileBytes
                GraphFilter.WIFI -> pt.wifiBytes
                else -> pt.totalBytes
            }
        } ?: 1024L
        maxOf(maxVal, 1024L * 1024L) // at least 1MB scale for clean rendering
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .pointerInput(points) {
                detectTapGestures { offset ->
                    if (points.isNotEmpty()) {
                        val colWidth = size.width / points.size.toFloat()
                        val tappedIndex = (offset.x / colWidth).toInt().coerceIn(0, points.size - 1)
                        onTapPoint(points[tappedIndex])
                    }
                }
            }
            .testTag("timeline_canvas_area")
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val width = size.width
            val height = size.height
            val bottomPadding = 24.dp.toPx()
            val graphHeight = height - bottomPadding

            // 1. Draw horizontal gridlines (0%, 50%, 100%)
            val gridY50 = graphHeight * 0.5f
            drawLine(color = gridColor, start = Offset(0f, 0f), end = Offset(width, 0f), strokeWidth = 1f)
            drawLine(color = gridColor, start = Offset(0f, gridY50), end = Offset(width, gridY50), strokeWidth = 1f)
            drawLine(color = gridColor, start = Offset(0f, graphHeight), end = Offset(width, graphHeight), strokeWidth = 1f)

            if (points.isEmpty()) return@Canvas

            val colWidth = width / points.size.toFloat()
            val barSpacing = (colWidth * 0.25f).coerceAtLeast(2f)
            val barWidth = (colWidth - barSpacing).coerceAtLeast(3f)

            points.forEachIndexed { index, point ->
                val xLeft = index * colWidth + (barSpacing / 2f)

                val valTotal = when (filter) {
                    GraphFilter.MOBILE -> point.mobileBytes
                    GraphFilter.WIFI -> point.wifiBytes
                    else -> point.totalBytes
                }

                val valDl = point.downloadBytes
                val valUl = point.uploadBytes

                val totalRatio = (valTotal.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
                val totalH = (totalRatio * graphHeight).coerceAtLeast(if (valTotal > 0) 4f else 0f)

                val dlRatio = if (valTotal > 0) (valDl.toFloat() / valTotal.toFloat()) else 0.5f
                val dlH = totalH * dlRatio
                val ulH = totalH - dlH

                val yBottom = graphHeight

                // Draw Download portion (Green)
                if (dlH > 0) {
                    drawRoundRect(
                        color = downloadColor,
                        topLeft = Offset(xLeft, yBottom - dlH),
                        size = Size(barWidth, dlH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }

                // Draw Upload portion (Blue, stacked above download)
                if (ulH > 0) {
                    drawRoundRect(
                        color = uploadColor,
                        topLeft = Offset(xLeft, yBottom - totalH),
                        size = Size(barWidth, ulH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }

                // Selected column indicator
                if (selectedPoint?.timestamp == point.timestamp || selectedPoint?.startTime == point.startTime) {
                    drawRoundRect(
                        color = selectionOverlayColor,
                        topLeft = Offset(index * colWidth, 0f),
                        size = Size(colWidth, graphHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = selectionBorderColor,
                        topLeft = Offset(xLeft - 1f, yBottom - totalH - 2f),
                        size = Size(barWidth + 2f, totalH + 4f),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // X-axis date / hour labels below the graph
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = (points.size / 5).coerceAtLeast(1)
            points.forEachIndexed { index, point ->
                if (index % step == 0 || index == points.size - 1) {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Analytical Inspector for the tapped interval:
 * Communicates:
 * - Applications active during that period
 * - Domains contacted
 * - Data consumed
 * - Blocked traffic vs Allowed traffic
 */
@Composable
fun IntervalTelemetryInspector(
    details: GraphIntervalDetails,
    onAppTapped: (String, Int) -> Unit,
    onDomainTapped: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interval_telemetry_inspector"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Interval Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Interval Activity: ${details.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Data Consumed: ${DataFormatter.formatBytes(details.totalDataConsumed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Allowed: ${DataFormatter.formatBytesShort(details.allowedBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (details.blockedBytes > 0 || details.blockedEvents.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Blocked: ${DataFormatter.formatBytesShort(details.blockedBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rx and Tx split
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "↑ Upload: ${DataFormatter.formatBytes(details.uploadBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF3B82F6)
                )
                Text(
                    text = "↓ Download: ${DataFormatter.formatBytes(details.downloadBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "Cellular: ${DataFormatter.formatBytesShort(details.mobileBytes)} • Wi-Fi: ${DataFormatter.formatBytesShort(details.wifiBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp), thickness = 0.5.dp)

            // Active Applications during interval
            Text(
                text = "ACTIVE APPLICATIONS (${details.activeApps.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (details.activeApps.isEmpty()) {
                Text(
                    text = "No app traffic recorded in this interval",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    details.activeApps.take(5).forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAppTapped(app.packageName, app.uid) }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.appName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (app.isBlocked) {
                                    Text(
                                        text = "BLOCKED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Text(
                                    text = DataFormatter.formatBytes(app.totalBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Domains Contacted during interval
            Text(
                text = "DOMAINS CONTACTED (${details.contactedDomains.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (details.contactedDomains.isEmpty()) {
                Text(
                    text = "No domain flows logged in this interval",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    details.contactedDomains.take(5).forEach { domain ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDomainTapped(domain.domain) }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = domain.domain,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (domain.associatedApps.isNotEmpty()) {
                                        Text(
                                            text = domain.associatedApps.joinToString(", "),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (domain.isBlocked) {
                                    Text(
                                        text = "BLOCKED",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Text(
                                    text = DataFormatter.formatBytes(domain.totalBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
