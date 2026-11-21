package com.dorobistro.inventory.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dorobistro.inventory.data.Supplier

@Composable
fun SuppliersScreen(vm: InventoryViewModel) {
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Supplier?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Supplier?>(null) }
    val itemCounts = items.groupingBy { it.supplierId }.eachCount()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Suppliers", "${suppliers.size} suppliers · lead times drive the order-by dates in Forecast") {
            Button(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add supplier")
            }
        }
        if (suppliers.isEmpty()) {
            EmptyState(Icons.Outlined.LocalShipping, "No suppliers yet", "Add suppliers so the reorder list can group orders by who you buy from.") {
                Button(onClick = { creating = true }) { Text("Add a supplier") }
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(suppliers, key = { it.id }) { s ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.LocalShipping, Tone.Info)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                val n = itemCounts[s.id] ?: 0
                                Text("$n item${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ContactLine(Icons.Outlined.Schedule, "Delivers ${s.leadTimeDays} day${if (s.leadTimeDays == 1) "" else "s"} after ordering")
                        if (s.contactName.isNotBlank()) ContactLine(Icons.Outlined.Person, s.contactName)
                        if (s.phone.isNotBlank()) ContactLine(Icons.Outlined.Phone, s.phone)
                        if (s.email.isNotBlank()) ContactLine(Icons.Outlined.Email, s.email)
                        if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { deleting = s }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { editing = s }) { Text("Edit") }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        SupplierEditorDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveSupplier(it); creating = false; editing = null },
        )
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete ${s.name}?",
            text = "Items from this supplier are kept, but they won't be linked to a supplier any more.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteSupplier(s); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ContactLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text)
    }
}

@Composable
private fun SupplierEditorDialog(initial: Supplier?, onDismiss: () -> Unit, onSave: (Supplier) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var contact by remember { mutableStateOf(initial?.contactName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var lead by remember { mutableStateOf((initial?.leadTimeDays ?: 2).toString()) }
    val leadDays = lead.trim().toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add supplier" else "Edit supplier") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact person") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = lead,
                    onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) lead = v },
                    label = { Text("Lead time") },
                    suffix = { Text("days") },
                    singleLine = true,
                    isError = leadDays == null,
                    supportingText = { Text("Days from placing an order to delivery. The forecast uses this to tell you when to order.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, placeholder = { Text("Delivery days, minimum order…") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && leadDays != null, onClick = {
                onSave(
                    (initial ?: Supplier(name = "")).copy(
                        name = name.trim(), contactName = contact.trim(), phone = phone.trim(), email = email.trim(), notes = notes.trim(),
                        leadTimeDays = leadDays ?: 2,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
