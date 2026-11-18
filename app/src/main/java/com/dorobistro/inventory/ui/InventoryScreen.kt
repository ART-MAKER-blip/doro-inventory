package com.dorobistro.inventory.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.Supplier
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.suggestedOrder
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

@Composable
fun InventoryScreen(vm: InventoryViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var lowOnly by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var creating by remember { mutableStateOf(false) }

    val categories = remember(items) { items.map { it.category }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() } }
    val supplierNames = remember(suppliers) { suppliers.associate { it.id to it.name } }
    val filtered = remember(items, query, category, lowOnly) {
        items.filter {
            (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true)) &&
                (category == null || it.category == category) &&
                (!lowOnly || it.isLow())
        }
    }
    val selected = items.firstOrNull { it.id == selectedId }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 720.dp
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            ItemRow(
                                item = item,
                                supplierName = item.supplierId?.let { supplierNames[it] },
                                selected = item.id == selectedId,
                                onClick = { selectedId = item.id },
                                onMinus = { vm.quickAdjust(item, -1.0) },
                                onPlus = { vm.quickAdjust(item, 1.0) },
                            )
                        }
                    }
                }
            }
        }

        val detail: @Composable (Item) -> Unit = { item ->
            ItemDetailPane(
                item = item,
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
                VerticalDivider()
                Box(Modifier.weight(0.85f).fillMaxHeight()) {
                    if (selected != null) detail(selected)
                    else EmptyState(Icons.Outlined.TouchApp, "Select an item", "Tap an item to see its details, adjust stock and view its history.")
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
private fun ItemRow(
    item: Item,
    supplierName: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) cs.secondaryContainer else cs.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
            FilledTonalIconButton(onClick = onMinus, enabled = item.quantity > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Remove, "Use one")
            }
            Column(Modifier.widthIn(min = 84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.quantity.fmt(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(item.unit, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Add, "Add one")
            }
        }
    }
}

@Composable
private fun ItemDetailPane(
    item: Item,
    supplier: Supplier?,
    vm: InventoryViewModel,
    showBack: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
) {
    val history by remember(item.id) { vm.movementsFor(item.id) }.collectAsStateWithLifecycle(emptyList())
    var adjustType by remember { mutableStateOf<MovementType?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(if (showBack) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close, "Close")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit item") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete item") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(item.category.ifBlank { "Uncategorized" }, color = cs.onSurfaceVariant)
                    StatusPills(item)
                }
                Card(colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Bottom) {
                        Text(item.quantity.fmt(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                        Text(" ${item.unit} on hand", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { adjustType = MovementType.DELIVERY }) {
                        Icon(MovementType.DELIVERY.icon(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Receive")
                    }
                    FilledTonalButton(onClick = { adjustType = MovementType.USAGE }) {
                        Icon(MovementType.USAGE.icon(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Use")
                    }
                    FilledTonalButton(onClick = { adjustType = MovementType.WASTE }) {
                        Icon(MovementType.WASTE.icon(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Waste")
                    }
                    FilledTonalButton(onClick = { adjustType = MovementType.COUNT }) {
                        Icon(MovementType.COUNT.icon(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Count")
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Fact("Minimum level", "${item.minQuantity.fmt()} ${item.unit}")
                        Fact("Par level", if (item.parLevel > 0) "${item.parLevel.fmt()} ${item.unit}" else "Not set")
                        if (item.isLow()) Fact("Suggested order", "${item.suggestedOrder().fmt()} ${item.unit}")
                        Fact("Cost per unit", if (item.costPerUnit > 0) money(item.costPerUnit) else "Not set")
                        Fact("Stock value", money(item.quantity * item.costPerUnit))
                        Fact("Supplier", supplier?.name ?: "None")
                        Fact("Expiry date", item.expiryEpochDay?.let { formatDay(it) } ?: "None")
                        Fact("Last updated", formatTime(item.updatedAt))
                        if (item.notes.isNotBlank()) Fact("Notes", item.notes)
                    }
                }
                Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
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

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.3f))
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
