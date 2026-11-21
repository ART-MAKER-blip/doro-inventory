package com.dorobistro.inventory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Forecast
import com.dorobistro.inventory.data.ForecastStatus
import com.dorobistro.inventory.data.Forecasting
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.Supplier
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.recommendedOrder
import com.dorobistro.inventory.data.targetLevel
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MS = 86_400_000L

private enum class SortBy(val label: String) { Name("Name"), Status("Needs attention first"), DaysLeft("Days of stock left") }

@Composable
fun InventoryScreen(vm: InventoryViewModel, messenger: Messenger) {
    val items by vm.items.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var lowOnly by rememberSaveable { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.Name) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var creating by remember { mutableStateOf(false) }

    val categories = remember(items) { items.map { it.category }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() } }
    val supplierNames = remember(suppliers) { suppliers.associate { it.id to it.name } }
    val filtered = remember(items, query, category, lowOnly, sortBy, forecasts) {
        val list = items.filter {
            (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true)) &&
                (category == null || it.category == category) &&
                (!lowOnly || it.isLow())
        }
        when (sortBy) {
            SortBy.Name -> list
            SortBy.Status -> list.sortedWith(compareBy({ it.stockTone().ordinal * -1 }, { forecasts[it.id]?.daysOfStock ?: Double.MAX_VALUE }))
            SortBy.DaysLeft -> list.sortedBy { forecasts[it.id]?.daysOfStock ?: Double.MAX_VALUE }
        }
    }
    val selected = items.firstOrNull { it.id == selectedId }

    val quick: (Item, Double) -> Unit = { item, delta ->
        vm.quickAdjust(item, delta) { movementId ->
            if (movementId != null) {
                val verb = if (delta > 0) "Added" else "Used"
                messenger.showWithAction("$verb 1 ${item.unit} of ${item.name}", "Undo") {
                    vm.undo(movementId) { ok -> if (!ok) messenger.show("Can't undo: ${item.name} has changed since") }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 760.dp
        BackHandler(enabled = !twoPane && selected != null) { selectedId = null }

        val list: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                ScreenHeader("Inventory", "${items.size} items · ${categories.size} categories") {
                    Button(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add item")
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search items or categories") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortMenu(sortBy) { sortBy = it }
                    VerticalDivider(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 24.dp))
                    FilterChip(selected = lowOnly, onClick = { lowOnly = !lowOnly }, label = { Text("Low stock only") })
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                    }
                }
                if (items.isEmpty()) {
                    EmptyState(Icons.Outlined.Inventory2, "No items yet", "Add the ingredients and supplies you want to track.") {
                        Button(onClick = { creating = true }) { Text("Add your first item") }
                    }
                } else if (filtered.isEmpty()) {
                    EmptyState(Icons.Outlined.Search, "No matches", "Try a different search or clear the filters.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            ItemRow(
                                item = item,
                                forecast = forecasts[item.id],
                                supplierName = item.supplierId?.let { supplierNames[it] },
                                selected = item.id == selectedId,
                                onClick = { selectedId = item.id },
                                onMinus = { quick(item, -1.0) },
                                onPlus = { quick(item, 1.0) },
                            )
                        }
                    }
                }
            }
        }

        val detail: @Composable (Item) -> Unit = { item ->
            ItemDetailPane(
                item = item,
                forecast = forecasts[item.id],
                supplier = suppliers.firstOrNull { it.id == item.supplierId },
                vm = vm,
                showBack = !twoPane,
                onClose = { selectedId = null },
                onEdit = { editing = item },
            )
        }

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(1f).fillMaxHeight())
                Box(
                    Modifier.weight(0.9f).fillMaxHeight().padding(top = 16.dp, end = 20.dp, bottom = 20.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                ) {
                    if (selected != null) detail(selected)
                    else EmptyState(Icons.Outlined.TouchApp, "Select an item", "Tap an item to see its forecast, adjust stock and view its history.")
                }
            }
        } else {
            if (selected != null) detail(selected) else list(Modifier.fillMaxSize())
        }
    }

    if (creating || editing != null) {
        ItemEditorDialog(
            initial = editing,
            categories = categories,
            suppliers = suppliers,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveItem(it); creating = false; editing = null },
        )
    }
}

@Composable
private fun SortMenu(current: SortBy, onSelect: (SortBy) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text("Sort: ${current.label}") })
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortBy.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { onSelect(s); open = false })
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: Item,
    forecast: Forecast?,
    supplierName: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onClick, selected = selected) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPills(item)
                }
                Text(
                    listOfNotNull(item.category.ifBlank { null }, supplierName).joinToString(" · ").ifBlank { "Uncategorized" },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StockLevelBar(item, Modifier.widthIn(max = 220.dp).weight(1f, fill = false).fillMaxWidth())
                    val left = daysLeftText(forecast)
                    if (left != null) {
                        val tone = when {
                            (forecast?.daysOfStock ?: 99.0) < 3 -> Tone.Danger
                            (forecast?.daysOfStock ?: 99.0) < 7 -> Tone.Warning
                            else -> Tone.Neutral
                        }
                        Text(left, style = MaterialTheme.typography.labelMedium, color = if (tone == Tone.Neutral) cs.onSurfaceVariant else tone.strong())
                    }
                }
            }
            FilledTonalIconButton(
                onClick = onMinus,
                enabled = item.quantity > 0,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.surfaceContainerHigh),
            ) {
                Icon(Icons.Outlined.Remove, "Use one")
            }
            Column(Modifier.widthIn(min = 84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.quantity.fmt(), style = MaterialTheme.typography.titleLarge)
                Text(item.unit, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            FilledTonalIconButton(
                onClick = onPlus,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
            ) {
                Icon(Icons.Outlined.Add, "Add one")
            }
        }
    }
}

@Composable
private fun ItemDetailPane(
    item: Item,
    forecast: Forecast?,
    supplier: Supplier?,
    vm: InventoryViewModel,
    showBack: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
) {
    val history by remember(item.id) { vm.movementsFor(item.id) }.collectAsStateWithLifecycle(emptyList())
    val window by vm.windowMovements.collectAsStateWithLifecycle()
    var adjustType by remember { mutableStateOf<MovementType?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val levels = remember(window, item) {
        Forecasting.stockLevels(item, window.filter { it.itemId == item.id }, 14, LocalDate.now(), ZoneId.systemDefault())
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(if (showBack) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close, "Close")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit")
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete item", tint = cs.error) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(item.category.ifBlank { "Uncategorized" }, supplier?.name).joinToString(" · "),
                        color = cs.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPills(item)
                        if (forecast != null && forecast.status != ForecastStatus.HEALTHY) ForecastBadge(forecast)
                    }
                }
                // On-hand summary
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.primary).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ON HAND", style = MaterialTheme.typography.labelSmall, color = cs.onPrimary.copy(alpha = 0.75f))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(item.quantity.fmt(), style = MaterialTheme.typography.displaySmall, color = cs.onPrimary)
                            Text(" ${item.unit}", style = MaterialTheme.typography.titleMedium, color = cs.onPrimary, modifier = Modifier.padding(bottom = 6.dp))
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Value", style = MaterialTheme.typography.labelSmall, color = cs.onPrimary.copy(alpha = 0.75f))
                                Text(money(item.quantity * item.costPerUnit), style = MaterialTheme.typography.titleMedium, color = cs.onPrimary)
                            }
                        }
                        daysLeftText(forecast)?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary.copy(alpha = 0.9f))
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MovementType.DELIVERY to "Receive",
                        MovementType.USAGE to "Use",
                        MovementType.WASTE to "Waste",
                        MovementType.COUNT to "Count",
                    ).forEach { (t, label) ->
                        OutlinedButton(onClick = { adjustType = t }) {
                            Icon(t.icon(), null, Modifier.size(18.dp), tint = t.tone().strong()); Spacer(Modifier.width(8.dp)); Text(label)
                        }
                    }
                }

                // Forecast
                SectionCard("Stock forecast", subtitle = forecast?.let { forecastSubtitle(it) }) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StockProjectionChart(
                            history = levels,
                            dailyUsage = if ((forecast?.dataDays ?: 0) >= Forecasting.MIN_DATA_DAYS) forecast?.dailyUsage ?: 0.0 else 0.0,
                            minLevel = item.minQuantity,
                            parLevel = item.targetLevel(),
                            unit = item.unit,
                        )
                        if (forecast != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                SmallFact("Usage / day", if (forecast.dailyUsage > 0) "${forecast.dailyUsage.fmt1()} ${item.unit}" else "–")
                                SmallFact("Runs out", forecast.stockOutDay?.let { relativeDay(it) } ?: "–")
                                SmallFact("Order by", if (forecast.status == ForecastStatus.ORDER_NOW) "Today" else forecast.orderByDay?.takeIf { forecast.dailyUsage > 0 }?.let { relativeDay(it) } ?: "–")
                                SmallFact("Suggested", item.recommendedOrder(forecast).let { if (it > 0) "${it.fmt()} ${item.unit}" else "–" })
                            }
                            TrendLabel(forecast.trendPercent)
                        }
                    }
                }

                // Details
                SectionCard("Details") {
                    Fact("Minimum level", "${item.minQuantity.fmt()} ${item.unit}")
                    Fact("Par level", if (item.parLevel > 0) "${item.parLevel.fmt()} ${item.unit}" else "Not set")
                    Fact("Cost per unit", if (item.costPerUnit > 0) money(item.costPerUnit) else "Not set")
                    Fact("Supplier", supplier?.let { "${it.name} (${it.leadTimeDays}-day lead time)" } ?: "None")
                    Fact("Expiry date", item.expiryEpochDay?.let { formatDay(it) } ?: "None")
                    Fact("Last updated", formatTime(item.updatedAt))
                    if (item.notes.isNotBlank()) Fact("Notes", item.notes)
                }
                Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (history.isEmpty()) {
            item { Text("No movements recorded yet.", color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
        }
        items(history, key = { it.movement.id }) { MovementRow(it, showItemName = false, modifier = Modifier.padding(horizontal = 8.dp)) }
    }

    adjustType?.let { type ->
        AdjustDialog(
            item = item,
            initialType = type,
            onDismiss = { adjustType = null },
            onConfirm = { t, amount, note -> vm.adjust(item.id, t, amount, note); adjustType = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${item.name}?",
            text = "This removes the item and its whole stock history from this tablet. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; onClose(); vm.deleteItem(item) },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun forecastSubtitle(f: Forecast): String = when (f.status) {
    ForecastStatus.LEARNING -> "Learning: needs ${Forecasting.MIN_DATA_DAYS} days of history"
    ForecastStatus.NO_USAGE -> "No usage in the last ${f.dataDays} days"
    else -> "Based on the last ${f.dataDays} days · ${f.leadTimeDays}-day lead time"
}

@Composable
private fun SmallFact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f))
    }
}

@Composable
fun AdjustDialog(
    item: Item,
    initialType: MovementType,
    initialAmount: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (MovementType, Double, String) -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var amount by remember { mutableStateOf(initialAmount?.fieldText() ?: "") }
    var note by remember { mutableStateOf("") }
    val value = parseNum(amount)
    val newQty = value?.let { if (type == MovementType.COUNT) it else (item.quantity + it * type.sign).coerceAtLeast(0.0) }
    val valid = value != null && (type == MovementType.COUNT || value > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MovementType.DELIVERY, MovementType.USAGE, MovementType.WASTE, MovementType.COUNT).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) },
                            leadingIcon = { Icon(t.icon(), null, Modifier.size(18.dp)) },
                        )
                    }
                }
                NumberField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = if (type == MovementType.COUNT) "Counted quantity" else "Amount",
                    suffix = item.unit,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = {
                        Text(
                            when (type) {
                                MovementType.DELIVERY -> "Invoice number"
                                MovementType.WASTE -> "Spoiled, dropped, expired…"
                                MovementType.USAGE -> "Dinner service, prep…"
                                else -> ""
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Now ${item.quantity.fmt()} ${item.unit}  →  After ${newQty?.fmt() ?: "–"} ${item.unit}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onConfirm(type, value!!, note.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ItemEditorDialog(
    initial: Item?,
    categories: List<String>,
    suppliers: List<Supplier>,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var qty by remember { mutableStateOf(initial?.quantity?.fieldText() ?: "") }
    var min by remember { mutableStateOf(initial?.minQuantity?.fieldText() ?: "") }
    var par by remember { mutableStateOf(initial?.parLevel?.fieldText() ?: "") }
    var cost by remember { mutableStateOf(initial?.costPerUnit?.fieldText() ?: "") }
    var supplierId by remember { mutableStateOf(initial?.supplierId) }
    var expiry by remember { mutableStateOf(initial?.expiryEpochDay) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showDate by remember { mutableStateOf(false) }

    val numbersOk = listOf(qty, min, par, cost).all { it.isBlank() || parseNum(it) != null }
    val valid = name.isNotBlank() && unit.isNotBlank() && numbersOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add item" else "Edit item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true,
                    placeholder = { Text("Produce, Meat, Dairy…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Suggestions(categories.filter { it != category }) { category = it }
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Suggestions(COMMON_UNITS.filter { it != unit }) { unit = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (initial == null) NumberField(qty, { qty = it }, "Starting qty", Modifier.weight(1f))
                    NumberField(min, { min = it }, "Min level", Modifier.weight(1f))
                    NumberField(par, { par = it }, "Par level", Modifier.weight(1f))
                }
                Text(
                    "You'll get a low-stock alert at the min level. The reorder list suggests enough to get back to par.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberField(cost, { cost = it }, "Cost per unit", Modifier.fillMaxWidth())
                SupplierPicker(suppliers, supplierId) { supplierId = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDate = true }) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(expiry?.let { "Expires ${formatDay(it)}" } ?: "Set expiry date")
                    }
                    if (expiry != null) TextButton(onClick = { expiry = null }) { Text("Clear") }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                val base = initial ?: Item(name = "", category = "", unit = "")
                onSave(
                    base.copy(
                        name = name.trim(),
                        category = category.trim(),
                        unit = unit.trim(),
                        quantity = if (initial == null) parseNum(qty) ?: 0.0 else base.quantity,
                        minQuantity = parseNum(min) ?: 0.0,
                        parLevel = parseNum(par) ?: 0.0,
                        costPerUnit = parseNum(cost) ?: 0.0,
                        supplierId = supplierId,
                        expiryEpochDay = expiry,
                        notes = notes.trim(),
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (expiry ?: LocalDate.now().toEpochDay()) * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { expiry = Math.floorDiv(it, DAY_MS) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun Suggestions(options: List<String>, onPick: (String) -> Unit) {
    if (options.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) }
    }
}

@Composable
private fun SupplierPicker(suppliers: List<Supplier>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = suppliers.firstOrNull { it.id == selectedId }?.name ?: "No supplier"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Supplier") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No supplier") }, onClick = { onSelect(null); expanded = false })
            if (suppliers.isNotEmpty()) HorizontalDivider()
            suppliers.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = { onSelect(s.id); expanded = false })
            }
        }
    }
}
