package com.dorobistro.inventory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.InsightReport
import com.dorobistro.inventory.data.Insights
import com.dorobistro.inventory.data.ItemAmount
import com.dorobistro.inventory.data.Tip
import com.dorobistro.inventory.data.TipKind
import com.dorobistro.inventory.ui.theme.status
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

/**
 * Cost & waste insights: what the kitchen used and threw away, how that compares with the period
 * before, and plain-language suggestions (lower a par level, raise one for rising demand, and so on).
 */
@Composable
fun InsightsScreen(vm: InventoryViewModel, onNavigate: (Dest) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val window by vm.windowMovements.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(30) }

    val report = remember(items, window, forecasts, days) {
        Insights.build(items, window, forecasts, days, LocalDate.now(), ZoneId.systemDefault(), ::money) { it.fmt() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Cost & waste insights", "Where your stock money goes, and how to waste less") {
            listOf(7, 14, 30).forEach { d ->
                FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d days") })
            }
        }
        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.Insights, "No data yet", "Insights appear once you add items and record deliveries, usage and waste.")
            return@Column
        }
        Column(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Kpis(report)
            DailyChartCard(report)
            BoxWithConstraints {
                val wide = maxWidth >= 860.dp
                val wasteCard: @Composable (Modifier) -> Unit = { m -> TopList("Most wasted", "By value, and share of what was used", report.topWaste, Tone.Danger, showShare = true, modifier = m) }
                val usageCard: @Composable (Modifier) -> Unit = { m -> TopList("Biggest food costs", "Value of stock used", report.topUsage, Tone.Info, showShare = false, modifier = m) }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        wasteCard(Modifier.weight(1f))
                        usageCard(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        wasteCard(Modifier.fillMaxWidth())
                        usageCard(Modifier.fillMaxWidth())
                    }
                }
            }
            TipsCard(report.tips, onNavigate)
            Text(
                "Values use each item's current cost per unit. Items without a cost are not included in totals.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class InsightKpi(val label: String, val value: String, val icon: ImageVector, val tone: Tone, val supporting: String)

@Composable
private fun Kpis(r: InsightReport) {
    val kpis = listOf(
        InsightKpi("Stock used", money(r.usageValue), Icons.Outlined.Restaurant, Tone.Info, delta(r.usageValue, r.prevUsageValue, r.days) ?: "Food cost of usage"),
        InsightKpi(
            "Waste", money(r.wasteValue), Icons.Outlined.DeleteOutline,
            if (r.wasteValue > 0) Tone.Danger else Tone.Success,
            delta(r.wasteValue, r.prevWasteValue, r.days) ?: "Thrown away or spoiled",
        ),
        InsightKpi(
            "Waste rate", r.wasteRate?.let { "${(it * 100).fmt1()}%" } ?: "–", Icons.Outlined.Percent,
            when {
                r.wasteRate == null -> Tone.Neutral
                r.wasteRate < 0.04 -> Tone.Success
                r.wasteRate < 0.08 -> Tone.Warning
                else -> Tone.Danger
            },
            "Share of consumed stock that was wasted",
        ),
        InsightKpi("Deliveries", money(r.deliveryValue), Icons.Outlined.LocalShipping, Tone.Accent, "Value of stock received"),
        InsightKpi(
            "Count shortfalls", money(r.countLossValue), Icons.Outlined.ReportProblem,
            if (r.countLossValue > 0) Tone.Warning else Tone.Success,
            "Missing stock found during counts",
        ),
    )
    AdaptiveGrid(kpis, minCellWidth = 200.dp) { k, mod ->
        KpiTile(k.label, k.value, k.icon, k.tone, mod, supporting = k.supporting)
    }
}

/** "▲ 12% vs previous 30 days", or null if there's nothing to compare with. */
private fun delta(current: Double, previous: Double, days: Int): String? {
    if (previous <= 0.0) return null
    val pct = (current - previous) / previous * 100
    val arrow = if (pct >= 0) "▲" else "▼"
    return "$arrow ${abs(pct).toInt()}% vs previous $days days"
}

@Composable
private fun DailyChartCard(r: InsightReport) {
    val cs = MaterialTheme.colorScheme
    val usageColor = cs.primary
    val wasteColor = MaterialTheme.status.danger
    val gridColor = cs.outlineVariant
    val top = max(r.daily.maxOfOrNull { it.usage + it.waste } ?: 0.0, 1.0)
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    SectionCard("Daily stock used", Modifier.fillMaxWidth(), subtitle = "Value per day, last ${r.days} days") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Peak ${money(top)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val n = r.daily.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                for (g in 1..3) {
                    val gy = size.height * g / 4f
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                }
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
                r.daily.forEachIndexed { i, d ->
                    val x = i * slot + (slot - barW) / 2
                    val uh = (d.usage / top * size.height).toFloat()
                    val wh = (d.waste / top * size.height).toFloat()
                    if (uh > 0f) drawRoundRect(usageColor, Offset(x, size.height - uh), Size(barW, uh), radius)
                    if (wh > 0f) drawRoundRect(wasteColor, Offset(x, size.height - uh - wh), Size(barW, wh), radius)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                r.daily.firstOrNull()?.let { Text(it.day.format(fmt), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                Spacer(Modifier.weight(1f))
                r.daily.lastOrNull()?.let { Text("Today", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChartKey(usageColor, "Used")
                ChartKey(wasteColor, "Wasted")
            }
        }
    }
}

@Composable
private fun ChartKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopList(title: String, subtitle: String, rows: List<ItemAmount>, tone: Tone, showShare: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    SectionCard(title, modifier, subtitle = subtitle) {
        if (rows.isEmpty()) {
            InlineNote(Icons.Outlined.CheckCircle, Tone.Success, if (showShare) "No waste recorded in this period." else "No usage recorded in this period.")
        }
        val maxValue = rows.maxOfOrNull { max(it.value, 0.0) }?.takeIf { it > 0 } ?: 1.0
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (r.value > 0) money(r.value) else "–", style = MaterialTheme.typography.titleSmall)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(cs.surfaceContainerHighest)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((r.value / maxValue).toFloat().coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(50)).background(tone.strong())
                    )
                }
                Text(
                    "${r.quantity.fmt1()} ${r.item.unit}" + if (showShare) " · ${(r.share * 100).toInt()}% of what was used" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TipsCard(tips: List<Tip>, onNavigate: (Dest) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SectionCard("Smart suggestions", Modifier.fillMaxWidth(), subtitle = "Generated from your usage, waste and forecast") {
        if (tips.isEmpty()) {
            InlineNote(Icons.Outlined.CheckCircle, Tone.Success, "No suggestions right now. Stock levels match how you're using them.")
        }
        tips.forEachIndexed { i, tip ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
            val (icon, tone, dest) = when (tip.kind) {
                TipKind.WASTE -> Triple(Icons.Outlined.DeleteOutline, Tone.Danger, Dest.Inventory)
                TipKind.DEMAND_UP -> Triple(Icons.Outlined.TrendingUp, Tone.Warning, Dest.Forecast)
                TipKind.OVERSTOCK -> Triple(Icons.Outlined.Inventory2, Tone.Info, Dest.Inventory)
                TipKind.SETUP -> Triple(Icons.Outlined.Settings, Tone.Neutral, if (tip.title.contains("supplier", true)) Dest.Suppliers else Dest.Inventory)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(icon, tone, 38.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tip.title, style = MaterialTheme.typography.titleSmall)
                    Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                TextButton(onClick = { onNavigate(dest) }) { Text(if (dest == Dest.Forecast) "Forecast" else if (dest == Dest.Suppliers) "Suppliers" else "Inventory") }
            }
        }
    }
}
