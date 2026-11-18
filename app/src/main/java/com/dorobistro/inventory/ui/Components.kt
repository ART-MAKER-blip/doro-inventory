package com.dorobistro.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.MovementWithItem
import com.dorobistro.inventory.data.daysUntilExpiry
import com.dorobistro.inventory.data.isExpiringSoon
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.isOut
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// ---------- formatting ----------

fun Double.fmt(): String =
    if (this == floor(this) && abs(this) < 1e12) toLong().toString()
    else String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

fun Double.signed(): String = if (this > 0) "+${fmt()}" else fmt()
fun Double.fieldText(): String = if (this == 0.0) "" else fmt()
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
fun money(v: Double): String = NumberFormat.getCurrencyInstance().format(v)

private val timeFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

fun formatTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)
fun formatClock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(clockFmt)
fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFmt)
fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dayFmt)
    }
}

val COMMON_UNITS = listOf("pcs", "kg", "g", "L", "ml", "box", "case", "bottle", "pack", "bunch", "tray")

// ---------- movement styling ----------

fun MovementType.icon(): ImageVector = when (this) {
    MovementType.DELIVERY -> Icons.Outlined.LocalShipping
    MovementType.USAGE -> Icons.Outlined.Restaurant
    MovementType.WASTE -> Icons.Outlined.Delete
    MovementType.COUNT -> Icons.Outlined.Checklist
    MovementType.ADJUSTMENT -> Icons.Outlined.Tune
}

@Composable
fun changeColor(change: Double): Color =
    if (change >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

// ---------- shared composables ----------

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusPills(item: Item) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            item.isOut() -> Pill("Out", cs.errorContainer, cs.onErrorContainer)
            item.isLow() -> Pill("Low", cs.tertiaryContainer, cs.onTertiaryContainer)
        }
        if (item.isExpiringSoon()) {
            val d = item.daysUntilExpiry() ?: 0
            val text = when {
                d < 0 -> "Expired"
                d == 0L -> "Expires today"
                d == 1L -> "Expires tomorrow"
                else -> "Expires in ${d}d"
            }
            Pill(text, if (d < 0) cs.errorContainer else cs.secondaryContainer, if (d < 0) cs.onErrorContainer else cs.onSecondaryContainer)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp).padding(top = 4.dp, bottom = 16.dp),
        )
        action()
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(v) },
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotBlank() && parseNum(value) == null,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MovementRow(m: MovementWithItem, showItemName: Boolean, modifier: Modifier = Modifier) {
    val mv = m.movement
    val color = changeColor(mv.change)
    val details = buildList {
        if (showItemName) add(mv.type.label)
        if (mv.note.isNotBlank()) add(mv.note)
        add(if (showItemName) formatClock(mv.timestamp) else formatTime(mv.timestamp))
    }.joinToString(" · ")
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(mv.type.icon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        headlineContent = { Text(if (showItemName) m.itemName else mv.type.label, maxLines = 1) },
        supportingContent = { Text(details, maxLines = 2) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text("${mv.change.signed()} ${m.itemUnit}", color = color, fontWeight = FontWeight.SemiBold)
                Text("→ ${mv.quantityAfter.fmt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
