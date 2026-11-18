package com.dorobistro.inventory.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.MovementType

@Composable
fun HistoryScreen(vm: InventoryViewModel) {
    val movements by vm.recentMovements.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<MovementType?>(null) }
    val filtered = remember(movements, filter) { movements.filter { filter == null || it.movement.type == filter } }
    val grouped = remember(filtered) { filtered.groupBy { localDate(it.movement.timestamp) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Stock history", "Every delivery, usage, waste and count correction")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
            MovementType.entries.forEach { t ->
                FilterChip(selected = filter == t, onClick = { filter = if (filter == t) null else t }, label = { Text(t.label) })
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(Icons.Outlined.History, "No history yet", "Stock changes are logged here automatically.")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            grouped.forEach { (day, list) ->
                item(key = "d-$day") {
                    Text(
                        dayLabel(day),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.movement.id }) { MovementRow(it, showItemName = true) }
            }
        }
    }
}
