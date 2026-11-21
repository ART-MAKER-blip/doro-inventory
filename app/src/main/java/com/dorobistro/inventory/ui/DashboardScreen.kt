package com.dorobistro.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Forecasting
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.isExpiringSoon
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.isOut
import com.dorobistro.inventory.data.needsOrder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class Kpi(val label: String, val value: String, val icon: ImageVector, val tone: Tone, val supporting: String, val dest: Dest)

@Composable
fun DashboardScreen(vm: InventoryViewModel, onNavigate: (Dest) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val movements by vm.recentMovements.collectAsStateWithLifecycle()
    val window by vm.windowMovements.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    val low = items.filter { it.isLow() }
    val expiring = items.filter { it.isExpiringSoon() }
    val value = items.sumOf { it.quantity * it.costPerUnit }
    val toOrder = items.filter { it.needsOrder(forecasts[it.id]) }
    val predictedOnly = toOrder.count { !it.isLow() }
    val runningOut = items
        .mapNotNull { item -> forecasts[item.id]?.daysOfStock?.takeIf { it <= 7.0 }?.let { item to it } }
        .sortedBy { it.second }
    val attention = (items.filter { it.isOut() } + low + expiring).distinctBy { it.id }
    val weekWaste = remember(window, items) {
        val costs = items.associate { it.id to it.costPerUnit }
        val since = LocalDate.now().minusDays(6).toEpochDay()
        val zone = ZoneId.systemDefault()
        window.filter { it.type == MovementType.WASTE && Forecasting.epochDay(it.timestamp, zone) >= since }
            .sumOf { -it.change * (costs[it.itemId] ?: 0.0) }
    }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(greeting, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))) {
            if (toOrder.isNotEmpty()) {
                Button(onClick = { onNavigate(Dest.Reorder) }) {
                    Icon(Icons.Outlined.ShoppingCart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Review orders (${toOrder.size})")
                }
            }
        }

        Column(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (items.isEmpty()) {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        IconBadge(Icons.Outlined.Inventory2, Tone.Info, 56.dp)
                        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Welcome to D'oro Inventory", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Add the ingredients and supplies you want to track, or load sample bistro data with four weeks of history " +
                                    "to see how the stock forecast and cost insights work. You can delete the samples at any time.",
                                color = cs.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Button(onClick = { onNavigate(Dest.Inventory) }) { Text("Add items") }
                                OutlinedButton(onClick = { vm.loadSampleData() }) { Text("Load sample data") }
                            }
                        }
                    }
                }
                return@Column
            }

            val kpis = listOf(
                Kpi("Stock value", money(value), Icons.Outlined.Payments, Tone.Info, "${items.size} items tracked", Dest.Inventory),
                Kpi(
                    "To order", toOrder.size.toString(), Icons.Outlined.ShoppingCart,
                    if (toOrder.isEmpty()) Tone.Success else Tone.Danger,
                    if (toOrder.isEmpty()) "Nothing needed right now" else "${low.size} below minimum · $predictedOnly predicted",
                    Dest.Reorder,
                ),
                Kpi(
                    "Running out in 7 days", runningOut.size.toString(), Icons.Outlined.Schedule,
                    if (runningOut.isEmpty()) Tone.Success else Tone.Warning, "From the usage forecast", Dest.Forecast,
                ),
                Kpi(
                    "Expiring in 3 days", expiring.size.toString(), Icons.Outlined.Event,
                    if (expiring.isEmpty()) Tone.Success else Tone.Accent, "Use these first", Dest.Inventory,
                ),
                Kpi(
                    "Waste, last 7 days", money(weekWaste), Icons.Outlined.DeleteOutline,
                    if (weekWaste > 0) Tone.Danger else Tone.Success, "See Insights for details", Dest.Insights,
                ),
            )
            AdaptiveGrid(kpis, minCellWidth = 200.dp) { k, mod ->
                KpiTile(k.label, k.value, k.icon, k.tone, mod, supporting = k.supporting, onClick = { onNavigate(k.dest) })
            }

            BoxWithConstraints {
                val wide = maxWidth >= 860.dp
                val forecastCard: @Composable (Modifier) -> Unit = { mod ->
                    SectionCard(
                        "Predicted to run out",
                        mod,
                        subtitle = "Next 7 days, based on recent usage",
                        action = { TextButton(onClick = { onNavigate(Dest.Forecast) }) { Text("Open forecast") } },
                    ) {
                        if (runningOut.isEmpty()) {
                            InlineNote(Icons.Outlined.CheckCircle, Tone.Success, "Nothing is forecast to run out this week.")
                        }
                        runningOut.take(6).forEachIndexed { i, (item, days) ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    StockLevelBar(item, Modifier.fillMaxWidth(0.85f))
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
                                    Text(
                                        if (days < 1) "Today" else "${days.toInt()} days",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = (if (days < 3) Tone.Danger else Tone.Warning).strong(),
                                    )
                                    Text("${item.quantity.fmt()} ${item.unit} left", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                val attentionCard: @Composable (Modifier) -> Unit = { mod ->
                    SectionCard(
                        "Needs attention",
                        mod,
                        subtitle = "Out of stock, below minimum or expiring",
                        action = { TextButton(onClick = { onNavigate(Dest.Inventory) }) { Text("Inventory") } },
                    ) {
                        if (attention.isEmpty()) {
                            InlineNote(Icons.Outlined.CheckCircle, Tone.Success, "Everything is stocked and fresh.")
                        }
                        attention.take(6).forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    StatusPills(item)
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
                                    Text("${item.quantity.fmt()} ${item.unit}", style = MaterialTheme.typography.titleSmall)
                                    Text("min ${item.minQuantity.fmt()}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        forecastCard(Modifier.weight(1f))
                        attentionCard(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        forecastCard(Modifier.fillMaxWidth())
                        attentionCard(Modifier.fillMaxWidth())
                    }
                }
            }

            SectionCard(
                "Recent activity",
                Modifier.fillMaxWidth(),
                action = { TextButton(onClick = { onNavigate(Dest.History) }) { Text("All history") } },
            ) {
                if (movements.isEmpty()) {
                    InlineNote(Icons.Outlined.AutoGraph, Tone.Info, "No stock movements yet. Deliveries, usage and waste will appear here.")
                }
                movements.take(6).forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
                    MovementRow(m, showItemName = true, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** Small icon + sentence used for empty sections inside cards. */
@Composable
fun InlineNote(icon: ImageVector, tone: Tone, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tone.strong(), modifier = Modifier.size(20.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
