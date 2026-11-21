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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Forecast
import com.dorobistro.inventory.data.ForecastStatus
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.Supplier
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.isOut
import com.dorobistro.inventory.data.needsOrder
import com.dorobistro.inventory.data.recommendedOrder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Why an item is on the list, shown as a badge. */
private fun reason(item: Item, f: Forecast?): Pair<String, Tone> = when {
    item.isOut() -> "Out of stock" to Tone.Danger
    item.isLow() -> "Below minimum" to Tone.Danger
    f != null && f.status == ForecastStatus.ORDER_NOW -> (f.stockOutDay?.let { "Runs out ${relativeDayInline(it)}" } ?: "Order now") to Tone.Danger
    f != null && f.status == ForecastStatus.ORDER_SOON -> (f.orderByDay?.let { "Order ${relativeDayInline(it)}" } ?: "Order soon") to Tone.Warning
    else -> "Suggested" to Tone.Neutral
}

private fun isUrgent(item: Item, f: Forecast?): Boolean = item.isLow() || f?.status == ForecastStatus.ORDER_NOW

@Composable
fun ReorderScreen(vm: InventoryViewModel, showMessage: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var receiving by remember { mutableStateOf<Item?>(null) }
    var urgentOnly by rememberSaveable { mutableStateOf(false) }

    val all = items.filter { it.needsOrder(forecasts[it.id]) }
    val list = if (urgentOnly) all.filter { isUrgent(it, forecasts[it.id]) } else all
    val urgentCount = all.count { isUrgent(it, forecasts[it.id]) }
    val byId = suppliers.associateBy { it.id }
    val groups = list
        .sortedWith(compareBy({ !isUrgent(it, forecasts[it.id]) }, { it.name.lowercase() }))
        .groupBy { it.supplierId?.let(byId::get) }
        .toList()
        .sortedWith(compareBy({ it.first == null }, { it.first?.name?.lowercase() }))
    val qtyOf: (Item) -> Double = { it.recommendedOrder(forecasts[it.id]) }
    val total = list.sumOf { qtyOf(it) * it.costPerUnit }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Reorder list",
            if (all.isEmpty()) "Items appear here when they're below minimum or forecast to run short"
            else "${list.size} items · estimated ${money(total)} · grouped by supplier",
        ) {
            if (list.isNotEmpty()) {
                OutlinedButton(onClick = {
                    shareText(context, "Order list", groups.joinToString("\n\n") { (s, l) -> orderText(s, l, qtyOf) })
                }) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share all")
                }
            }
        }
        if (all.isEmpty()) {
            EmptyState(Icons.Outlined.CheckCircle, "Everything is stocked", "Nothing is below its minimum and nothing is forecast to run short before the next delivery.")
            return@Column
        }
        Row(Modifier.padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !urgentOnly, onClick = { urgentOnly = false }, label = { Text("Everything to order (${all.size})") })
            FilterChip(selected = urgentOnly, onClick = { urgentOnly = true }, label = { Text("Order today only ($urgentCount)") })
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(groups, key = { it.first?.id ?: -1L }) { (supplier, l) ->
                SupplierOrderCard(
                    supplier = supplier,
                    items = l,
                    forecasts = forecasts,
                    qtyOf = qtyOf,
                    onShare = { shareText(context, "Order for ${supplier?.name ?: "unassigned items"}", orderText(supplier, l, qtyOf)) },
                    onReceive = { receiving = it },
                )
            }
        }
    }

    receiving?.let { item ->
        AdjustDialog(
            item = item,
            initialType = MovementType.DELIVERY,
            initialAmount = qtyOf(item),
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
private fun SupplierOrderCard(
    supplier: Supplier?,
    items: List<Item>,
    forecasts: Map<Long, Forecast>,
    qtyOf: (Item) -> Double,
    onShare: () -> Unit,
    onReceive: (Item) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val subtotal = items.sumOf { qtyOf(it) * it.costPerUnit }
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.LocalShipping, if (supplier == null) Tone.Neutral else Tone.Info)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(supplier?.name ?: "No supplier assigned", style = MaterialTheme.typography.titleMedium)
                val contact = listOfNotNull(
                    supplier?.let { "Delivers in ${it.leadTimeDays} day${if (it.leadTimeDays == 1) "" else "s"}" },
                    supplier?.contactName?.ifBlank { null },
                    supplier?.phone?.ifBlank { null },
                    supplier?.email?.ifBlank { null },
                ).joinToString(" · ")
                if (contact.isNotBlank()) Text(contact, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                Text(if (subtotal > 0) money(subtotal) else "–", style = MaterialTheme.typography.titleMedium)
                Text("${items.size} item${if (items.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
            }
        }
        HorizontalDivider(color = cs.outlineVariant)
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
            val f = forecasts[item.id]
            val (why, tone) = reason(item, f)
            val qty = qtyOf(item)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        StatusBadge(why, tone)
                    }
                    StockLevelBar(item, Modifier.widthIn(max = 260.dp).fillMaxWidth())
                    val usage = f?.dailyUsage?.takeIf { it > 0 }?.let { " · uses ${it.fmt1()}/day" } ?: ""
                    Text(
                        "Have ${item.quantity.fmt()} · min ${item.minQuantity.fmt()} ${item.unit}$usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("${qty.fmt()} ${item.unit}", style = MaterialTheme.typography.titleMedium)
                    if (item.costPerUnit > 0) {
                        Text(money(qty * item.costPerUnit), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                FilledTonalButton(onClick = { onReceive(item) }) { Text("Received") }
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

private fun orderText(supplier: Supplier?, items: List<Item>, qtyOf: (Item) -> Double): String = buildString {
    appendLine("Order: ${supplier?.name ?: "No supplier"} (${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))})")
    items.forEach { appendLine("• ${it.name}: ${qtyOf(it).fmt()} ${it.unit}") }
}.trimEnd()

private fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share order"))
}
