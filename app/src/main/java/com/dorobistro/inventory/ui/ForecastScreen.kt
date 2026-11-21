package com.dorobistro.inventory.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Forecast
import com.dorobistro.inventory.data.ForecastStatus
import com.dorobistro.inventory.data.Forecasting
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.Supplier
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.recommendedOrder
import com.dorobistro.inventory.data.targetLevel
import com.dorobistro.inventory.ui.theme.status
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

private enum class ForecastFilter(val label: String) { All("All items"), Now("Order now"), Soon("Order soon"), Healthy("Healthy"), Other("Learning / idle") }

private fun ForecastFilter.matches(s: ForecastStatus): Boolean = when (this) {
    ForecastFilter.All -> true
    ForecastFilter.Now -> s == ForecastStatus.ORDER_NOW
    ForecastFilter.Soon -> s == ForecastStatus.ORDER_SOON
    ForecastFilter.Healthy -> s == ForecastStatus.HEALTHY
    ForecastFilter.Other -> s == ForecastStatus.LEARNING || s == ForecastStatus.NO_USAGE
}

private fun ForecastStatus.rank(): Int = when (this) {
    ForecastStatus.ORDER_NOW -> 0
    ForecastStatus.ORDER_SOON -> 1
    ForecastStatus.HEALTHY -> 2
    ForecastStatus.LEARNING -> 3
    ForecastStatus.NO_USAGE -> 4
}

@Composable
fun ForecastScreen(vm: InventoryViewModel, onNavigate: (Dest) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    val window by vm.windowMovements.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(ForecastFilter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val supplierById = remember(suppliers) { suppliers.associateBy { it.id } }

    val rows = remember(items, forecasts) {
        items.mapNotNull { item -> forecasts[item.id]?.let { item to it } }
            .sortedWith(
                compareBy<Pair<Item, Forecast>> { it.second.status.rank() }
                    .thenBy { it.second.orderByDay ?: Long.MAX_VALUE }
                    .thenBy { it.second.daysOfStock ?: Double.MAX_VALUE }
                    .thenBy { it.first.name.lowercase() }
            )
    }
    val counts = ForecastFilter.entries.associateWith { f -> rows.count { f.matches(it.second.status) } }
    val visible = rows.filter { (item, f) ->
        filter.matches(f.status) && (query.isBlank() || item.name.contains(query, true) || item.category.contains(query, true))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Predictive replenishment",
            "Forecasts from the last ${Forecasting.LOOKBACK_DAYS} days of usage and each supplier's lead time",
        ) {
            TextButton(onClick = { showHelp = !showHelp }) {
                Icon(Icons.Outlined.Lightbulb, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (showHelp) "Hide explanation" else "How it works")
            }
            if ((counts[ForecastFilter.Now] ?: 0) + (counts[ForecastFilter.Soon] ?: 0) > 0) {
                Button(onClick = { onNavigate(Dest.Reorder) }) {
                    Icon(Icons.Outlined.ShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reorder list")
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.AutoGraph, "No items to forecast", "Add items and record deliveries and usage. Forecasts appear after a few days of history.")
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHelp) item(key = "help") { HowItWorksCard() }
            item(key = "summary") {
                val tiles = listOf(
                    Triple(ForecastFilter.Now, Icons.Outlined.WarningAmber, Tone.Danger),
                    Triple(ForecastFilter.Soon, Icons.Outlined.HourglassEmpty, Tone.Warning),
                    Triple(ForecastFilter.Healthy, Icons.Outlined.CheckCircle, Tone.Success),
                    Triple(ForecastFilter.Other, Icons.Outlined.AutoGraph, Tone.Info),
                )
                AdaptiveGrid(tiles, minCellWidth = 190.dp, modifier = Modifier.padding(bottom = 4.dp)) { (f, icon, tone), mod ->
                    val support = when (f) {
                        ForecastFilter.Now -> "Below minimum or arriving too late if not ordered today"
                        ForecastFilter.Soon -> "Order within the next ${Forecasting.SOON_DAYS} days"
                        ForecastFilter.Healthy -> "Enough stock to get past the next delivery"
                        else -> "Need more history, or not being used"
                    }
                    KpiTile(f.label, "${counts[f] ?: 0}", icon, tone, mod, supporting = support, onClick = { filter = if (filter == f) ForecastFilter.All else f })
                }
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search items or categories") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ForecastFilter.entries.forEach { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} (${counts[f] ?: 0})") })
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "none") {
                    AppCard(Modifier.fillMaxWidth()) {
                        InlineNote(Icons.Outlined.CheckCircle, Tone.Success, "No items match this filter.")
                    }
                }
            } else {
                item(key = "table") {
                    ForecastTable(visible, supplierById) { detailId = it }
                }
            }
        }
    }

    val detail = detailId?.let { id -> items.firstOrNull { it.id == id } }
    val detailForecast = detail?.let { forecasts[it.id] }
    if (detail != null && detailForecast != null) {
        ForecastDetailDialog(
            item = detail,
            forecast = detailForecast,
            supplier = detail.supplierId?.let { supplierById[it] },
            history = remember(window, detail) {
                Forecasting.stockLevels(detail, window.filter { it.itemId == detail.id }, 14, LocalDate.now(), ZoneId.systemDefault())
            },
            onDismiss = { detailId = null },
            onReorder = { detailId = null; onNavigate(Dest.Reorder) },
        )
    }
}

@Composable
private fun HowItWorksCard() {
    val cs = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(Icons.Outlined.Lightbulb, Tone.Accent)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How the forecast works", style = MaterialTheme.typography.titleMedium)
                val lines = listOf(
                    "Daily usage" to "Everything that left the shelf (usage, waste and shortfalls found in stock counts) over the last ${Forecasting.LOOKBACK_DAYS} days. The most recent week counts most, so busier or quieter weeks show up quickly.",
                    "Runs out" to "Stock on hand ÷ daily usage.",
                    "Order by" to "The last day you can order and still receive it in time: the supplier's lead time plus a ${Forecasting.SAFETY_DAYS}-day safety buffer. Set each supplier's lead time on the Suppliers screen.",
                    "Suggested order" to "Enough to cover the lead time, the next ${Forecasting.COVER_DAYS} days and the safety buffer. If an item is below its minimum, the par level is used when that's larger.",
                    "Learning" to "New items need at least ${Forecasting.MIN_DATA_DAYS} days of history. Record usage with the − button or log stock counts, and the forecast improves over time.",
                )
                lines.forEach { (title, body) ->
                    Row {
                        Text("$title  ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastTable(rows: List<Pair<Item, Forecast>>, suppliers: Map<Long, Supplier>, onOpen: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 900.dp
        AppCard(Modifier.fillMaxWidth()) {
            if (wide) {
                Row(
                    Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderCell("Item", 2.2f)
                    HeaderCell("Usage / day", 1.2f)
                    HeaderCell("On hand", 1.4f)
                    HeaderCell("Runs out", 1.1f)
                    HeaderCell("Order by", 1.1f)
                    HeaderCell("Suggested", 1.1f)
                    HeaderCell("Status", 1.3f)
                }
                HorizontalDivider(color = cs.outlineVariant)
            }
            rows.forEachIndexed { i, (item, f) ->
                if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                val supplier = item.supplierId?.let { suppliers[it] }
                if (wide) WideRow(item, f, supplier) { onOpen(item.id) } else NarrowRow(item, f, supplier) { onOpen(item.id) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun WideRow(item: Item, f: Forecast, supplier: Supplier?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2.2f).padding(end = 12.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${supplier?.name ?: "No supplier"} · ${f.leadTimeDays}-day lead time",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.weight(1.2f)) {
            Text(if (f.dailyUsage > 0) "${f.dailyUsage.fmt1()} ${item.unit}" else "–", style = MaterialTheme.typography.bodyMedium)
            TrendLabel(f.trendPercent)
        }
        Column(Modifier.weight(1.4f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${item.quantity.fmt()} ${item.unit}", style = MaterialTheme.typography.bodyMedium)
            StockLevelBar(item, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1.1f)) {
            Text(f.stockOutDay?.let { relativeDay(it) } ?: "–", style = MaterialTheme.typography.bodyMedium)
            daysLeftText(f)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        }
        Text(
            orderByText(f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (f.needsOrder) FontWeight.SemiBold else FontWeight.Normal,
            color = if (f.needsOrder) f.status.tone().strong() else cs.onSurface,
            modifier = Modifier.weight(1.1f),
        )
        Text(
            item.recommendedOrder(f).let { if (it > 0) "${it.fmt()} ${item.unit}" else "–" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.1f),
        )
        Box(Modifier.weight(1.3f)) { ForecastBadge(f) }
    }
}

@Composable
private fun NarrowRow(item: Item, f: Forecast, supplier: Supplier?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(supplier?.name ?: "No supplier", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            ForecastBadge(f)
        }
        StockLevelBar(item, Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("On hand", "${item.quantity.fmt()} ${item.unit}")
            MiniStat("Usage / day", if (f.dailyUsage > 0) "${f.dailyUsage.fmt1()} ${item.unit}" else "–")
            MiniStat("Runs out", f.stockOutDay?.let { relativeDay(it) } ?: "–")
            MiniStat("Order by", orderByText(f))
            MiniStat("Suggested", item.recommendedOrder(f).let { if (it > 0) "${it.fmt()} ${item.unit}" else "–" })
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun orderByText(f: Forecast): String = when (f.status) {
    ForecastStatus.ORDER_NOW -> "Today"
    ForecastStatus.LEARNING, ForecastStatus.NO_USAGE -> "–"
    else -> f.orderByDay?.let { relativeDay(it) } ?: "–"
}

/** Arrow + percentage. Rising demand is amber (you may need more), falling is blue. */
@Composable
fun TrendLabel(trend: Double?) {
    if (trend == null) {
        Text("Not enough history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (icon, tone, text) = when {
        trend >= 10 -> Triple(Icons.Outlined.TrendingUp, Tone.Warning, "Up ${trend.toInt()}%")
        trend <= -10 -> Triple(Icons.Outlined.TrendingDown, Tone.Info, "Down ${abs(trend).toInt()}%")
        else -> Triple(Icons.Outlined.TrendingFlat, Tone.Neutral, "Steady")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = tone.strong())
        Text(text, style = MaterialTheme.typography.bodySmall, color = tone.strong())
    }
}

@Composable
private fun ForecastDetailDialog(
    item: Item,
    forecast: Forecast,
    supplier: Supplier?,
    history: List<Double>,
    onDismiss: () -> Unit,
    onReorder: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val qty = item.recommendedOrder(forecast)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 720.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name)
                    Text(
                        "${supplier?.name ?: "No supplier"} · ${forecast.leadTimeDays}-day lead time",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
                ForecastBadge(forecast)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StockProjectionChart(
                    history = history,
                    dailyUsage = if (forecast.dataDays >= Forecasting.MIN_DATA_DAYS) forecast.dailyUsage else 0.0,
                    minLevel = item.minQuantity,
                    parLevel = item.targetLevel(),
                    unit = item.unit,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat("On hand", "${item.quantity.fmt()} ${item.unit}")
                    MiniStat("Usage / day", if (forecast.dailyUsage > 0) "${forecast.dailyUsage.fmt1()} ${item.unit}" else "–")
                    MiniStat("Days of stock", forecast.daysOfStock?.let { if (it > 60) "60+" else it.fmt1() } ?: "–")
                    MiniStat("Runs out", forecast.stockOutDay?.let { shortDay(it) } ?: "–")
                    MiniStat("Order by", if (forecast.status == ForecastStatus.ORDER_NOW) "Today" else forecast.orderByDay?.let { shortDay(it) } ?: "–")
                    val costText = if (item.costPerUnit > 0) " · ${money(qty * item.costPerUnit)}" else ""
                    MiniStat("Suggested order", if (qty > 0) "${qty.fmt()} ${item.unit}$costText" else "–")
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerLow).padding(16.dp)) {
                    Text(explain(item, forecast, supplier), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (forecast.needsOrder || item.isLow()) {
                Button(onClick = onReorder) { Text("Go to reorder list") }
            } else {
                Button(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = { if (forecast.needsOrder || item.isLow()) TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Plain-language explanation of the numbers, so staff can trust (or question) the suggestion. */
private fun explain(item: Item, f: Forecast, supplier: Supplier?): String {
    val u = item.unit
    if (f.status == ForecastStatus.LEARNING) {
        return "There isn't enough history yet (${f.dataDays} of ${Forecasting.MIN_DATA_DAYS} days needed). " +
            "Keep recording usage, waste and deliveries and a forecast will appear." +
            if (item.isLow()) " The item is already below its minimum, so it is on the reorder list." else ""
    }
    if (f.dailyUsage <= 0) {
        return "Nothing has been used in the last ${f.dataDays} days, so no stock-out is expected." +
            if (item.isLow()) " It is below its minimum level, though, so it is on the reorder list." else ""
    }
    val sb = StringBuilder()
    sb.append("You use about ${f.dailyUsage.fmt1()} $u a day")
    f.trendPercent?.let { t ->
        when {
            t >= 10 -> sb.append(" (up ${t.toInt()}% this week)")
            t <= -10 -> sb.append(" (down ${abs(t).toInt()}% this week)")
        }
    }
    sb.append(". ")
    val days = f.daysOfStock ?: 0.0
    sb.append("The ${item.quantity.fmt()} $u on hand ")
    sb.append(if (days < 1) "will run out today. " else "lasts about ${days.fmt1()} days, until ${f.stockOutDay?.let { shortDay(it) }}. ")
    val who = supplier?.name ?: "Your supplier"
    sb.append("$who takes ${f.leadTimeDays} day${if (f.leadTimeDays == 1) "" else "s"} to deliver, and we keep a ${Forecasting.SAFETY_DAYS}-day safety buffer, ")
    sb.append(
        when (f.status) {
            ForecastStatus.ORDER_NOW -> "so you should order today. "
            else -> "so order by ${f.orderByDay?.let { shortDay(it) }}. "
        }
    )
    val qty = item.recommendedOrder(f)
    if (qty > 0) {
        sb.append("Ordering ${qty.fmt()} $u covers the delivery time plus the next ${Forecasting.COVER_DAYS} days.")
    }
    if (item.isLow()) sb.append(" It is also below its minimum level of ${item.minQuantity.fmt()} $u.")
    return sb.toString().trim()
}

/** Past 14 days of actual stock (solid) and the next 14 days projected from daily usage (dashed). */
@Composable
fun StockProjectionChart(
    history: List<Double>,
    dailyUsage: Double,
    minLevel: Double,
    parLevel: Double,
    unit: String,
    modifier: Modifier = Modifier,
    daysAhead: Int = 14,
) {
    val cs = MaterialTheme.colorScheme
    val st = MaterialTheme.status
    val actualColor = cs.primary
    val projColor = cs.tertiary
    val minColor = st.warning
    val parColor = st.success
    val gridColor = cs.outlineVariant
    val outColor = st.danger
    val current = history.lastOrNull() ?: 0.0
    val projection = (0..daysAhead).map { max(current - dailyUsage * it, 0.0) }
    val top = max(listOf(history.maxOrNull() ?: 0.0, parLevel, minLevel, current).max(), 1.0) * 1.12
    val steps = max(history.size - 1 + daysAhead, 1)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Float): Float = w * i / steps
            fun y(v: Double): Float = (h - v / top * h).toFloat()
            for (g in 1..3) {
                drawLine(gridColor, Offset(0f, h * g / 4f), Offset(w, h * g / 4f), strokeWidth = 1f)
            }
            drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.5f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            if (parLevel > 0) drawLine(parColor, Offset(0f, y(parLevel)), Offset(w, y(parLevel)), strokeWidth = 2f, pathEffect = dash)
            if (minLevel > 0) drawLine(minColor, Offset(0f, y(minLevel)), Offset(w, y(minLevel)), strokeWidth = 2f, pathEffect = dash)

            if (history.isNotEmpty()) {
                val line = Path()
                history.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i.toFloat()), y(v)) else line.lineTo(x(i.toFloat()), y(v)) }
                val area = Path()
                area.addPath(line)
                area.lineTo(x(history.lastIndex.toFloat()), h)
                area.lineTo(0f, h)
                area.close()
                drawPath(area, actualColor.copy(alpha = 0.10f))
                drawPath(line, actualColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val todayX = x(history.lastIndex.coerceAtLeast(0).toFloat())
            drawLine(cs.outline, Offset(todayX, 0f), Offset(todayX, h), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            if (dailyUsage > 0) {
                val proj = Path()
                projection.forEachIndexed { j, v ->
                    val px = x((history.lastIndex + j).toFloat())
                    if (j == 0) proj.moveTo(px, y(v)) else proj.lineTo(px, y(v))
                }
                drawPath(
                    proj, projColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))),
                )
                val outDays = current / dailyUsage
                if (outDays <= daysAhead) {
                    drawCircle(outColor, 6.dp.toPx(), Offset(x((history.lastIndex + outDays).toFloat()), h))
                }
            }
            drawCircle(Color.White, 6.dp.toPx(), Offset(todayX, y(current)))
            drawCircle(actualColor, 4.dp.toPx(), Offset(todayX, y(current)))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${history.size - 1} days ago", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("Today", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("+$daysAhead days", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendKey(actualColor, "Actual stock")
            if (dailyUsage > 0) LegendKey(projColor, "Forecast")
            if (parLevel > 0) LegendKey(parColor, "Par ${parLevel.fmt()} $unit")
            if (minLevel > 0) LegendKey(minColor, "Minimum ${minLevel.fmt()} $unit")
            if (dailyUsage > 0 && current / dailyUsage <= daysAhead) LegendKey(outColor, "Runs out", round = true)
        }
    }
}

@Composable
private fun LegendKey(color: Color, label: String, round: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(width = if (round) 10.dp else 16.dp, height = if (round) 10.dp else 4.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
