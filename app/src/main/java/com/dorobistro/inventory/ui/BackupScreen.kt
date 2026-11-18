package com.dorobistro.inventory.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun BackupScreen(vm: InventoryViewModel, showMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by vm.items.collectAsStateWithLifecycle()
    val suppliers by vm.suppliers.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't open the file")
                }
                showMessage("Backup saved")
            } catch (e: Exception) {
                showMessage("Backup failed: ${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                pendingImport = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: error("Couldn't read the file")
            } catch (e: Exception) {
                showMessage("Couldn't read the file: ${e.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Backup & restore", "Your data is stored only on this tablet")
        Column(Modifier.padding(horizontal = 24.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCard(
                Icons.Outlined.PhoneAndroid,
                "Stored on this device",
                "${items.size} items and ${suppliers.size} suppliers are saved in a database on this tablet. Nothing is uploaded. " +
                    "If the app is uninstalled or the tablet is reset, the data is gone, so export a backup regularly " +
                    "and keep it somewhere safe (a USB drive, email or cloud folder).",
            )
            BackupCard(
                Icons.Outlined.FileDownload,
                "Export backup",
                "Save everything (items, suppliers and full history) to a JSON file.",
            ) {
                Button(onClick = { exportLauncher.launch("doro-inventory-${LocalDate.now()}.json") }) { Text("Export") }
            }
            BackupCard(
                Icons.Outlined.FileUpload,
                "Restore from backup",
                "Replace everything on this tablet with the contents of a backup file.",
            ) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Restore")
                }
            }
            if (items.isEmpty()) {
                BackupCard(Icons.Outlined.PhoneAndroid, "Try it out", "Load a dozen sample bistro items and suppliers to explore the app.") {
                    OutlinedButton(onClick = { vm.loadSampleData(); showMessage("Sample data loaded") }) { Text("Load samples") }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    pendingImport?.let { json ->
        ConfirmDialog(
            title = "Replace all data?",
            text = "Everything currently on this tablet (${items.size} items, their history and suppliers) will be replaced with the backup. Export first if you want to keep it.",
            confirmLabel = "Replace",
            onConfirm = {
                pendingImport = null
                scope.launch {
                    try {
                        val n = vm.importJson(json)
                        showMessage("Restored $n items")
                    } catch (e: Exception) {
                        showMessage(e.message ?: "Restore failed")
                    }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(body, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun BackupCard(icon: ImageVector, title: String, body: String, action: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            action()
        }
    }
}
