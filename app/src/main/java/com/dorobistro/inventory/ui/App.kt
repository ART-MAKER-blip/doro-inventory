package com.dorobistro.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dorobistro.inventory.data.needsOrder
import com.dorobistro.inventory.ui.theme.status
import kotlinx.coroutines.launch

enum class Dest(val label: String, val icon: ImageVector, val section: String) {
    Dashboard("Dashboard", Icons.Outlined.SpaceDashboard, "Overview"),
    Inventory("Inventory", Icons.Outlined.Inventory2, "Stock"),
    Count("Stock count", Icons.Outlined.FactCheck, "Stock"),
    History("History", Icons.Outlined.History, "Stock"),
    Forecast("Forecast", Icons.Outlined.AutoGraph, "Planning"),
    Reorder("Reorder", Icons.Outlined.ShoppingCart, "Planning"),
    Insights("Insights", Icons.Outlined.Insights, "Planning"),
    Suppliers("Suppliers", Icons.Outlined.LocalShipping, "Manage"),
    Backup("Backup", Icons.Outlined.Backup, "Manage"),
}

/** Lets screens show a snackbar, optionally with an action such as "Undo". */
class Messenger(val show: (String) -> Unit, val showWithAction: (message: String, action: String, onAction: () -> Unit) -> Unit)

@Composable
fun DoroApp(vm: InventoryViewModel = viewModel()) {
    var dest by rememberSaveable { mutableStateOf(Dest.Dashboard) }
    val items by vm.items.collectAsStateWithLifecycle()
    val forecasts by vm.forecasts.collectAsStateWithLifecycle()
    val orderCount = items.count { it.needsOrder(forecasts[it.id]) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember {
        Messenger(
            show = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } },
            showWithAction = { msg, action, onAction ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(msg, actionLabel = action, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) onAction()
                }
            },
        )
    }
    val showMessage: (String) -> Unit = messenger.show

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val expanded = maxWidth >= 1100.dp
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    current = dest,
                    expanded = expanded,
                    badges = mapOf(Dest.Reorder to orderCount),
                    onSelect = { dest = it },
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (dest) {
                        Dest.Dashboard -> DashboardScreen(vm, onNavigate = { dest = it })
                        Dest.Inventory -> InventoryScreen(vm, messenger)
                        Dest.Count -> CountScreen(vm, showMessage)
                        Dest.History -> HistoryScreen(vm)
                        Dest.Forecast -> ForecastScreen(vm, onNavigate = { dest = it })
                        Dest.Reorder -> ReorderScreen(vm, showMessage)
                        Dest.Insights -> InsightsScreen(vm, onNavigate = { dest = it })
                        Dest.Suppliers -> SuppliersScreen(vm)
                        Dest.Backup -> BackupScreen(vm, showMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(current: Dest, expanded: Boolean, badges: Map<Dest, Int>, onSelect: (Dest) -> Unit) {
    val st = MaterialTheme.status
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (expanded) 248.dp else 96.dp)
            .background(st.sidebar)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (expanded) 14.dp else 10.dp, vertical = 16.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        // Brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (expanded) 6.dp else 0.dp, bottom = 20.dp),
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(st.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("D'", color = st.sidebar, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (expanded) {
                Column(Modifier.padding(start = 12.dp)) {
                    Text("D'oro", color = st.onSidebar, style = MaterialTheme.typography.titleMedium)
                    Text("Kitchen inventory", color = st.onSidebarMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        var lastSection = ""
        Dest.entries.forEach { d ->
            if (d.section != lastSection) {
                lastSection = d.section
                if (expanded) {
                    Text(
                        d.section.uppercase(),
                        color = st.onSidebarMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                    )
                } else if (d != Dest.entries.first()) {
                    Box(
                        Modifier.padding(vertical = 8.dp).width(32.dp).height(1.dp)
                            .background(st.onSidebarMuted.copy(alpha = 0.25f))
                    )
                }
            }
            SidebarItem(d, selected = d == current, expanded = expanded, badge = badges[d] ?: 0) { onSelect(d) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SidebarItem(dest: Dest, selected: Boolean, expanded: Boolean, badge: Int, onClick: () -> Unit) {
    val st = MaterialTheme.status
    val fg = if (selected) st.onSidebar else st.onSidebarMuted
    val shape = RoundedCornerShape(10.dp)
    val base = Modifier
        .padding(vertical = 2.dp)
        .clip(shape)
        .background(if (selected) st.sidebarSelected else st.sidebar)
        .clickable(onClick = onClick)
    if (expanded) {
        Row(
            base.fillMaxWidth().height(44.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) st.accent else st.sidebar))
            Icon(dest.icon, null, tint = if (selected) st.accent else fg, modifier = Modifier.padding(start = 11.dp).size(22.dp))
            Text(
                dest.label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp).weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge > 0) CountBubble(badge)
        }
    } else {
        Column(
            base.width(76.dp).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box {
                Icon(dest.icon, dest.label, tint = if (selected) st.accent else fg, modifier = Modifier.size(24.dp))
                if (badge > 0) Box(Modifier.align(Alignment.TopEnd).padding(start = 16.dp)) { CountBubble(badge, small = true) }
            }
            Text(
                dest.label,
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CountBubble(count: Int, small: Boolean = false) {
    val st = MaterialTheme.status
    Box(
        Modifier
            .clip(CircleShape)
            .background(st.danger)
            .padding(horizontal = if (small) 5.dp else 7.dp, vertical = if (small) 0.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            color = MaterialTheme.colorScheme.onError,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
