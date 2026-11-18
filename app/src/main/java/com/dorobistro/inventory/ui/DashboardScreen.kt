package com.dorobistro.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.isExpiringSoon
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.isOut
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(vm: InventoryViewModel, onNavigate: (Dest) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val movements by vm.recentMovements.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    val low = items.filter { it.isLow() }
    val out = items.filter { it.isOut() }
    val expiring = items.filter { it.isExpiringSoon() }
    val value = items.sumOf { it.quantity * it.costPerUnit }
    val attention = (out + low + expiring).distinctBy { it.id }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
        }

        if (items.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = cs.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your inventory is empty", style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer)
                    Text(
                        "Add your first items, or load some sample bistro data to try the app out. You can delete the samples at any time.",
                        color = cs.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { onNavigate(Dest.Inventory) }) { Text("Add items") }
                        OutlinedButton(onClick = { vm.loadSampleData() }) { Text("Load sample data") }
                    }
                }
            }
            return@Column
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Items tracked", items.size.toString(), Icons.Outlined.Inventory2, cs.surfaceContainerHigh, cs.onSurface) { onNavigate(Dest.Inventory) }
            StatCard("Low stock", low.size.toString(), Icons.Outlined.WarningAmber, cs.tertiaryContainer, cs.onTertiaryContainer) { onNavigate(Dest.Reorder) }
            StatCard("Out of stock", out.size.toString(), Icons.Outlined.ErrorOutline, cs.errorContainer, cs.onErrorContainer) { onNavigate(Dest.Reorder) }
            StatCard("Expiring ≤ 3 days", expiring.size.toString(), Icons.Outlined.Event, cs.secondaryContainer, cs.onSecondaryContainer) { onNavigate(Dest.Inventory) }
            StatCard("Stock value", money(value), Icons.Outlined.Payments, cs.surfaceContainerHigh, cs.onSurface) { onNavigate(Dest.Inventory) }
        }

        BoxWithConstraints {
            val wide = maxWidth >= 800.dp
            val attentionCard: @Composable (Modifier) -> Unit = { mod ->
                PanelCard("Needs attention", mod, action = { TextButton(onClick = { onNavigate(Dest.Reorder) }) { Text("Reorder list") } }) {
                    if (attention.isEmpty()) {
                        Text("Nothing to worry about. Everything is stocked.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    attention.take(8).forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Min ${item.minQuantity.fmt()} ${item.unit}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            StatusPills(item)
                            Text(
                                "${item.quantity.fmt()} ${item.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
            val activityCard: @Composable (Modifier) -> Unit = { mod ->
                PanelCard("Recent activity", mod, action = { TextButton(onClick = { onNavigate(Dest.History) }) { Text("All history") } }) {
                    if (movements.isEmpty()) {
                        Text("No stock movements yet.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    movements.take(8).forEach { MovementRow(it, showItemName = true) }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    attentionCard(Modifier.weight(1f))
                    activityCard(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    attentionCard(Modifier.fillMaxWidth())
                    activityCard(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, container: Color, content: Color, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = container, contentColor = content), modifier = Modifier.width(200.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PanelCard(
    title: String,
    modifier: Modifier,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            action()
        }
        Box(Modifier.padding(bottom = 8.dp)) { Column { content() } }
    }
}
