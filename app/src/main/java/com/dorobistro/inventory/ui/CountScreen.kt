package com.dorobistro.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Item

/** Walk-through stock take: enter what is physically on the shelf, save all corrections at once. */
@Composable
fun CountScreen(vm: InventoryViewModel, showMessage: (String) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val counts = remember { mutableStateMapOf<Long, String>() }
    var query by rememberSaveable { mutableStateOf("") }

    val changes = items.mapNotNull { item ->
        counts[item.id]?.let(::parseNum)?.takeIf { it != item.quantity }?.let { item.id to it }
    }.toMap()
    val grouped = items
        .filter { query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) }
        .groupBy { it.category.ifBlank { "Uncategorized" } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Stock count", "Enter what's actually on the shelf. Leave an item blank to skip it.") {
            if (counts.isNotEmpty()) TextButton(onClick = { counts.clear() }) { Text("Clear all") }
        }
        if (items.isEmpty()) {
            EmptyState(Icons.Outlined.Checklist, "Nothing to count", "Add items in Inventory first.")
            return@Column
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Jump to item or category") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            grouped.forEach { (cat, list) ->
                item(key = "h-$cat") {
                    Text(
                        cat,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { item ->
                    CountRow(item, counts[item.id] ?: "") { v -> if (v.isEmpty()) counts.remove(item.id) else counts[item.id] = v }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (changes.size) {
                        0 -> "No differences yet"
                        1 -> "1 item differs from the system"
                        else -> "${changes.size} items differ from the system"
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(enabled = changes.isNotEmpty(), onClick = {
                    vm.applyCounts(changes)
                    showMessage("Saved ${changes.size} count correction${if (changes.size == 1) "" else "s"}")
                    counts.clear()
                }) { Text("Save count") }
            }
        }
    }
}

@Composable
private fun CountRow(item: Item, value: String, onChange: (String) -> Unit) {
    val counted = parseNum(value)
    val diff = counted?.minus(item.quantity)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "System: ${item.quantity.fmt()} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NumberField(value, onChange, "Counted", Modifier.width(150.dp), suffix = item.unit)
            Text(
                when {
                    diff == null -> ""
                    diff == 0.0 -> "✓"
                    else -> diff.signed()
                },
                color = if (diff != null && diff != 0.0) changeColor(diff) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp),
            )
        }
    }
}
