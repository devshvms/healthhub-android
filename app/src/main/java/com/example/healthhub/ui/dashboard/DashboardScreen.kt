package com.example.healthhub.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthhub.HealthViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class DashboardMetric(val title: String, val color: Color) {
    STEPS("Steps", Color.Blue),
    HEART_RATE("Heart Rate", Color.Red),
    SLEEP("Sleep", Color(0xFF800080)),
    STRESS("Stress", Color.Black)
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: HealthViewModel, modifier: Modifier = Modifier) {
    val stepsData by viewModel.stepsData.collectAsStateWithLifecycle()
    val heartRateData by viewModel.heartRateData.collectAsStateWithLifecycle()
    val sleepData by viewModel.sleepData.collectAsStateWithLifecycle()
    val stressData by viewModel.stressData.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var selectedMetric by remember { mutableStateOf(DashboardMetric.STEPS) }

    val ptrState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.syncCurrentDayData() }
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize().pullRefresh(ptrState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text("Health Dashboard (Last 24h)", style = MaterialTheme.typography.headlineMedium)
            Text("Pull down to sync latest data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            // The Unified Chart
            Card(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedMetric.title, style = MaterialTheme.typography.titleMedium, color = selectedMetric.color)
                    }
                    
                    val now = Instant.now()
                    val oneDayAgo = now.minus(24, ChronoUnit.HOURS)
                    val offsetMinutes = oneDayAgo.epochSecond / 60L
                    val minX = 0f
                    val maxX = 1440f // 24 hours * 60 minutes
                    
                    val timeFormatter = remember(offsetMinutes) {
                        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                            val roundedValue = kotlin.math.round(value).toLong()
                            if (roundedValue % 240L == 0L) {
                                val instant = Instant.ofEpochSecond((offsetMinutes + roundedValue) * 60L)
                                val local = instant.atZone(ZoneId.systemDefault())
                                String.format("%02d:%02d", local.hour, local.minute)
                            } else {
                                ""
                            }
                        }
                    }

                    val (entries, summaryText) = remember(selectedMetric, stepsData, heartRateData, sleepData, stressData, offsetMinutes) {
                        val list = mutableListOf<FloatEntry>()
                        var text = ""
                        
                        val finalList = when (selectedMetric) {
                            DashboardMetric.STEPS -> {
                                var currentX = minX
                                var totalSteps = 0f
                                list.add(FloatEntry(currentX, totalSteps))
                                
                                val validSteps = stepsData.mapNotNull {
                                    try {
                                        val s = ((Instant.parse(it["startTime"] as String).epochSecond / 60L) - offsetMinutes).toFloat()
                                        val e = ((Instant.parse(it["endTime"] as String).epochSecond / 60L) - offsetMinutes).toFloat()
                                        val c = (it["count"] as? Number)?.toFloat() ?: 0f
                                        Triple(s, e, c)
                                    } catch (e: Exception) { null }
                                }.filter { it.second > minX }.sortedBy { it.first }
                                
                                // Summary: only sum steps whose endTime is within last 24h
                                val summarySteps = validSteps.filter { it.second >= minX && it.second <= maxX }.sumOf { it.third.toDouble() }.toFloat()
                                
                                for ((s, e, c) in validSteps) {
                                    val startX = maxOf(currentX, s)
                                    val endX = maxOf(startX, e)
                                    
                                    if (startX > currentX) {
                                        list.add(FloatEntry(startX, totalSteps)) // Flat gap
                                    }
                                    totalSteps += c
                                    if (endX > startX) {
                                        list.add(FloatEntry(endX, totalSteps)) // Ascending slope
                                    }
                                    currentX = endX
                                }
                                list.add(FloatEntry(maxX, totalSteps))
                                text = "Total: ${summarySteps.toInt()} steps"
                                
                                // Deduplicate X values (Vico requires unique X)
                                list.groupBy { it.x }.map { FloatEntry(it.key, it.value.last().y) }.sortedBy { it.x }
                            }
                            DashboardMetric.HEART_RATE -> {
                                val vals = mutableListOf<Float>()
                                heartRateData.forEach {
                                    val time = Instant.parse(it["time"] as String)
                                    if (time.isAfter(oneDayAgo) && time.isBefore(now)) {
                                        val x = ((time.epochSecond / 60L) - offsetMinutes).toFloat()
                                        val bpm = (it["beatsPerMinute"] as? Number)?.toFloat() ?: 0f
                                        list.add(FloatEntry(x, bpm))
                                        vals.add(bpm)
                                    }
                                }
                                if (vals.isNotEmpty()) {
                                    text = "Min: ${vals.minOrNull()?.toInt() ?: 0} | Max: ${vals.maxOrNull()?.toInt() ?: 0} | Mean: ${vals.average().toInt()}"
                                } else {
                                    text = "No data"
                                }
                                list.groupBy { it.x }.map { FloatEntry(it.key, it.value.map { it.y }.average().toFloat()) }.sortedBy { it.x }
                            }
                            DashboardMetric.SLEEP -> {
                                var totalSleep = 0f
                                sleepData.forEach {
                                    val time = Instant.parse(it["endTime"] as String)
                                    if (time.isAfter(oneDayAgo) && time.isBefore(now)) {
                                        val x = ((time.epochSecond / 60L) - offsetMinutes).toFloat()
                                        val duration = (it["durationHours"] as? Number)?.toFloat() ?: 0f
                                        list.add(FloatEntry(x, duration))
                                        totalSleep += duration
                                    }
                                }
                                text = String.format("Total: %.1f hrs", totalSleep)
                                list.groupBy { it.x }.map { FloatEntry(it.key, it.value.map { e -> e.y.toDouble() }.sum().toFloat()) }.sortedBy { it.x }
                            }
                            DashboardMetric.STRESS -> {
                                val vals = mutableListOf<Float>()
                                stressData.forEach {
                                    val time = Instant.parse(it["time"] as String)
                                    if (time.isAfter(oneDayAgo) && time.isBefore(now)) {
                                        val x = ((time.epochSecond / 60L) - offsetMinutes).toFloat()
                                        val level = (it["level"] as? Number)?.toFloat() ?: 0f
                                        list.add(FloatEntry(x, level))
                                        vals.add(level)
                                    }
                                }
                                if (vals.isNotEmpty()) {
                                    text = String.format("Min: %.1f | Max: %.1f | Mean: %.1f", vals.minOrNull() ?: 0f, vals.maxOrNull() ?: 0f, vals.average())
                                } else {
                                    text = "No data"
                                }
                                list.groupBy { it.x }.map { FloatEntry(it.key, it.value.map { it.y }.average().toFloat()) }.sortedBy { it.x }
                            }
                        }
                        
                        val safeList = if (finalList.isEmpty()) {
                            listOf(FloatEntry(minX, 0f), FloatEntry(maxX, 0f))
                        } else finalList
                        
                        Pair(safeList, text)
                    }

                    Text(summaryText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (entries.isNotEmpty()) {
                        Crossfade(targetState = selectedMetric) { metric ->
                            val model = remember(entries, minX, maxX) {
                                // Add invisible points every 4 hours (240 mins) to force Vico to draw X-axis labels at those intervals
                                val invisibleEntries = (0..6).map { FloatEntry(minX + it * 240f, 0f) }.toMutableList()
                                invisibleEntries.add(FloatEntry(minX + 1f, 0f)) // Force xStep to exactly 1f
                                entryModelOf(entries, invisibleEntries)
                            }
                            val scrollSpec = rememberChartScrollSpec<com.patrykandpatrick.vico.core.entry.ChartEntryModel>(isScrollEnabled = false)
                            
                            if (metric == DashboardMetric.SLEEP) {
                                Chart(
                                    chart = columnChart(
                                        columns = listOf(
                                            com.patrykandpatrick.vico.compose.component.lineComponent(
                                                color = metric.color,
                                                shape = com.patrykandpatrick.vico.core.component.shape.Shapes.roundedCornerShape(topLeftPercent = 25, topRightPercent = 25),
                                                thickness = 12.dp
                                            ),
                                            com.patrykandpatrick.vico.compose.component.lineComponent(
                                                color = androidx.compose.ui.graphics.Color.Transparent,
                                                thickness = 0.dp
                                            )
                                        )
                                    ),
                                    model = model,
                                    startAxis = rememberStartAxis(guideline = null),
                                    bottomAxis = rememberBottomAxis(
                                        valueFormatter = timeFormatter, 
                                        guideline = null,
                                        itemPlacer = com.patrykandpatrick.vico.core.axis.AxisItemPlacer.Horizontal.default(spacing = 240, offset = 0)
                                    ),
                                    chartScrollSpec = scrollSpec,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Chart(
                                    chart = lineChart(
                                        lines = listOf(
                                            com.patrykandpatrick.vico.compose.chart.line.lineSpec(lineColor = metric.color),
                                            com.patrykandpatrick.vico.compose.chart.line.lineSpec(
                                                lineColor = androidx.compose.ui.graphics.Color.Transparent,
                                                lineBackgroundShader = null
                                            )
                                        )
                                    ),
                                    model = model,
                                    startAxis = rememberStartAxis(guideline = null),
                                    bottomAxis = rememberBottomAxis(
                                        valueFormatter = timeFormatter, 
                                        guideline = null,
                                        itemPlacer = com.patrykandpatrick.vico.core.axis.AxisItemPlacer.Horizontal.default(spacing = 240, offset = 0)
                                    ),
                                    chartScrollSpec = scrollSpec,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No data for the last 24 hours.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Selector Buttons (Custom TabRow style)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardMetric.entries.forEach { metric ->
                    val isSelected = selectedMetric == metric
                    androidx.compose.material3.Surface(
                        selected = isSelected,
                        onClick = { selectedMetric = metric },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = metric.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
        
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = ptrState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}
