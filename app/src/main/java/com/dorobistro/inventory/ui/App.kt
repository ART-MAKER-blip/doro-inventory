package com.dorobistro.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dorobistro.inventory.data.isLow
import kotlinx.coroutines.launch

enum class Dest(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Outlined.Dashboard),
    Inventory("Inventory", Icons.Outlined.Inventory2),
    Count("Count", Icons.Outlined.Checklist),
    Reorder("Reorder", Icons.Outlined.ShoppingCart),
    History("History", Icons.Outlined.History),
    Suppliers("Suppliers", Icons.Outlined.LocalShipping),
    Backup("Backup", Icons.Outlined.Backup),
}

@Composable
fun DoroApp(vm: InventoryViewModel = viewModel()) {
    var dest by rememberSaveable { mutableStateOf(Dest.Dashboard) }
    val items by vm.items.collectAsStateWithLifecycle()
    val lowCount = items.count { it.isLow() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            NavigationRail(
                windowInsets = WindowInsets(0),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "D'",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text("D'oro", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                },
            ) {
                Dest.entries.forEach { d ->
                    NavigationRailItem(
                        selected = dest == d,
                        onClick = { dest = d },
                        icon = {
                            if (d == Dest.Reorder && lowCount > 0) {
                                BadgedBox(badge = { Badge { Text("$lowCount") } }) { Icon(d.icon, contentDescription = d.label) }
                            } else {
                                Icon(d.icon, contentDescription = d.label)
                            }
                        },
                        label = { Text(d.label) },
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (dest) {
                    Dest.Dashboard -> DashboardScreen(vm, onNavigate = { dest = it })
                    Dest.Inventory -> InventoryScreen(vm)
                    Dest.Count -> CountScreen(vm, showMessage)
                    Dest.Reorder -> ReorderScreen(vm, showMessage)
                    Dest.History -> HistoryScreen(vm)
                    Dest.Suppliers -> SuppliersScreen(vm)
                    Dest.Backup -> BackupScreen(vm, showMessage)
                }
            }
        }
    }
}
