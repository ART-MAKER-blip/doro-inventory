package com.dorobistro.inventory.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.Supplier
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.suggestedOrder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReorderScreen(vm: InventoryViewModel, showMessage: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var receiving by remember { mutableStateOf<Item?>(null) }

    val low = items.filter { it.isLow() }
    val byId = suppliers.associateBy { it.id }
    val groups = low.groupBy { it.supplierId?.let(byId::get) }
        .toList()
        .sortedWith(compareBy({ it.first == null }, { it.first?.name?.lowercase() }))
    val total = low.sumOf { it.suggestedOrder() * it.costPerUnit }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Reorder list",
            if (low.isEmpty()) "Items appear here when they reach their minimum level"
            else "${low.size} items to order · estimated ${money(total)}",
        ) {
            if (low.isNotEmpty()) {
                OutlinedButton(onClick = {
                    shareText(context, "Order list", groups.joinToString("\n\n") { (s, list) -> orderText(s, list) })
                }) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share all")
                }
            }
        }
        if (low.isEmpty()) {
            EmptyState(Icons.Outlined.CheckCircle, "Everything is stocked", "Nothing is at or below its minimum level right now.")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(groups, key = { it.first?.id ?: -1L }) { (supplier, list) ->
                SupplierOrderCard(
                    supplier = supplier,
                    items = list,
                    onShare = { shareText(context, "Order for ${supplier?.name ?: "unassigned items"}", orderText(supplier, list)) },
                    onReceive = { receiving = it },
                )
            }
        }
    }

    receiving?.let { item ->
        AdjustDialog(
            item = item,
            initialType = MovementType.DELIVERY,
            initialAmount = item.suggestedOrder(),
            onDismiss = { receiving = null },
            onConfirm = { t, amount, note ->
                vm.adjust(item.id, t, amount, note)
                showMessage("Recorded ${amount.fmt()} ${item.unit} of ${item.name}")
                receiving = null
            },
        )
    }
}

@Composable
private fun SupplierOrderCard(supplier: Supplier?, items: List<Item>, onShare: () -> Unit, onReceive: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val subtotal = items.sumOf { it.suggestedOrder() * it.costPerUnit }
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(supplier?.name ?: "No supplier assigned", style = MaterialTheme.typography.titleLarge)
                val contact = listOfNotNull(
                    supplier?.contactName?.ifBlank { null },
                    supplier?.phone?.ifBlank { null },
                    supplier?.email?.ifBlank { null },
                ).joinToString(" · ")
                if (contact.isNotBlank()) Text(contact, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (subtotal > 0) Text("Estimated ${money(subtotal)}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Share order") }
        }
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        StatusPills(item)
                    }
                    Text(
                        "Have ${item.quantity.fmt()} · min ${item.minQuantity.fmt()} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 16.dp)) {
                    Text("${item.suggestedOrder().fmt()} ${item.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (item.costPerUnit > 0) {
                        Text(money(item.suggestedOrder() * item.costPerUnit), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                FilledTonalButton(onClick = { onReceive(item) }) { Text("Received") }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

private fun orderText(supplier: Supplier?, items: List<Item>): String = buildString {
    appendLine("Order: ${supplier?.name ?: "No supplier"} (${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))})")
    items.forEach { appendLine("• ${it.name}: ${it.suggestedOrder().fmt()} ${it.unit}") }
}.trimEnd()

private fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share order"))
}
