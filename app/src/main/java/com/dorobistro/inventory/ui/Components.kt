package com.dorobistro.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dorobistro.inventory.data.Forecast
import com.dorobistro.inventory.data.ForecastStatus
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.MovementWithItem
import com.dorobistro.inventory.data.daysUntilExpiry
import com.dorobistro.inventory.data.isExpiringSoon
import com.dorobistro.inventory.data.isLow
import com.dorobistro.inventory.data.isOut
import com.dorobistro.inventory.data.targetLevel
import com.dorobistro.inventory.ui.theme.status
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

// ---------- formatting ----------

fun Double.fmt(): String =
    if (this == floor(this) && abs(this) < 1e12) toLong().toString()
    else String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

/** One decimal place at most, for rates like "1.6 kg/day". */
fun Double.fmt1(): String =
    if (abs(this) >= 10) Math.round(this).toString()
    else String.format(Locale.US, "%.1f", this).removeSuffix(".0")

fun Double.signed(): String = if (this > 0) "+${fmt()}" else fmt()
fun Double.fieldText(): String = if (this == 0.0) "" else fmt()
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
/** Prices are in Philippine pesos (₱), whatever the tablet's language setting. */
fun money(v: Double): String = pesoFormat.format(v)

private val pesoFormat: NumberFormat = NumberFormat.getCurrencyInstance(java.util.Locale("en", "PH")).apply { currency = java.util.Currency.getInstance("PHP") }

private val timeFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val shortDayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

fun formatTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)
fun formatClock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(clockFmt)
fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFmt)
fun shortDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDayFmt)
fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dayFmt)
    }
}

/** "Today", "Tomorrow", "In 3 days", or a short date for anything further out. */
fun relativeDay(epochDay: Long): String {
    val diff = epochDay - LocalDate.now().toEpochDay()
    return when {
        diff < 0 -> "Overdue"
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff < 7 -> "In $diff days"
        else -> shortDay(epochDay)
    }
}

/** For use mid-sentence: "today", "in 3 days", or "on Thu, Nov 27". */
fun relativeDayInline(epochDay: Long): String {
    val diff = epochDay - LocalDate.now().toEpochDay()
    return if (diff < 7) relativeDay(epochDay).lowercase() else "on ${shortDay(epochDay)}"
}

/** "About 4 days left", "Runs out today", or null when there is no usage to forecast from. */
fun daysLeftText(f: Forecast?): String? {
    val d = f?.daysOfStock ?: return null
    return when {
        d < 1 -> "Runs out today"
        d < 1.5 -> "About 1 day left"
        d > 60 -> "60+ days left"
        else -> "About ${d.toInt()} days left"
    }
}

val COMMON_UNITS = listOf("pcs", "kg", "g", "L", "ml", "box", "case", "bottle", "pack", "bunch", "tray")

// ---------- tones ----------

enum class Tone { Neutral, Info, Success, Warning, Danger, Accent }

@Composable
fun Tone.container(): Color = MaterialTheme.status.let {
    when (this) {
        Tone.Neutral -> it.neutralContainer
        Tone.Info -> it.infoContainer
        Tone.Success -> it.successContainer
        Tone.Warning -> it.warningContainer
        Tone.Danger -> it.dangerContainer
        Tone.Accent -> MaterialTheme.colorScheme.secondaryContainer
    }
}

@Composable
fun Tone.onContainer(): Color = MaterialTheme.status.let {
    when (this) {
        Tone.Neutral -> it.onNeutralContainer
        Tone.Info -> it.onInfoContainer
        Tone.Success -> it.onSuccessContainer
        Tone.Warning -> it.onWarningContainer
        Tone.Danger -> it.onDangerContainer
        Tone.Accent -> MaterialTheme.colorScheme.onSecondaryContainer
    }
}

@Composable
fun Tone.strong(): Color = MaterialTheme.status.let {
    when (this) {
        Tone.Neutral -> it.neutral
        Tone.Info -> it.info
        Tone.Success -> it.success
        Tone.Warning -> it.warning
        Tone.Danger -> it.danger
        Tone.Accent -> MaterialTheme.colorScheme.secondary
    }
}

fun ForecastStatus.tone(): Tone = when (this) {
    ForecastStatus.ORDER_NOW -> Tone.Danger
    ForecastStatus.ORDER_SOON -> Tone.Warning
    ForecastStatus.HEALTHY -> Tone.Success
    ForecastStatus.NO_USAGE -> Tone.Neutral
    ForecastStatus.LEARNING -> Tone.Info
}

fun Item.stockTone(): Tone = when {
    isOut() -> Tone.Danger
    isLow() -> Tone.Warning
    else -> Tone.Success
}

// ---------- movement styling ----------

fun MovementType.icon(): ImageVector = when (this) {
    MovementType.DELIVERY -> Icons.Outlined.LocalShipping
    MovementType.USAGE -> Icons.Outlined.Restaurant
    MovementType.WASTE -> Icons.Outlined.DeleteOutline
    MovementType.COUNT -> Icons.Outlined.Checklist
    MovementType.ADJUSTMENT -> Icons.Outlined.Tune
}

fun MovementType.tone(): Tone = when (this) {
    MovementType.DELIVERY -> Tone.Success
    MovementType.USAGE -> Tone.Info
    MovementType.WASTE -> Tone.Danger
    MovementType.COUNT, MovementType.ADJUSTMENT -> Tone.Neutral
}

@Composable
fun changeColor(change: Double): Color =
    if (change >= 0) MaterialTheme.status.success else MaterialTheme.status.danger

// ---------- layout building blocks ----------

/** White card with a hairline border: the basic container used across the app. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = CardDefaults.outlinedCardColors(
        containerColor = if (selected) cs.primaryContainer.copy(alpha = 0.55f) else cs.surface,
        contentColor = cs.onSurface,
    )
    val border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant)
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = modifier, colors = colors, border = border, content = content)
    } else {
        OutlinedCard(modifier = modifier, colors = colors, border = border, content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        actions()
    }
}

/** Card with a title row, optional action, and free content below. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            action()
        }
        content()
        Spacer(Modifier.height(8.dp))
    }
}

/** Lays [items] out in equal-width columns, as many as fit at [minCellWidth]. */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    cell: @Composable (T, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = max(1, ((maxWidth + spacing) / (minCellWidth + spacing)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { cell(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Small rounded square holding a tinted icon. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(tone.container()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tone.strong())
    }
}

@Composable
fun KpiTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconBadge(icon, tone, 34.dp)
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

// ---------- status ----------

@Composable
fun StatusBadge(text: String, tone: Tone, modifier: Modifier = Modifier, showDot: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(tone.container()).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showDot) Box(Modifier.size(6.dp).clip(CircleShape).background(tone.strong()))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tone.onContainer(), maxLines = 1)
    }
}

/** Kept for compatibility with older call sites. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun StatusPills(item: Item) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            item.isOut() -> StatusBadge("Out of stock", Tone.Danger)
            item.isLow() -> StatusBadge("Low", Tone.Warning)
        }
        if (item.isExpiringSoon()) {
            val d = item.daysUntilExpiry() ?: 0
            val text = when {
                d < 0 -> "Expired"
                d == 0L -> "Expires today"
                d == 1L -> "Expires tomorrow"
                else -> "Expires in ${d}d"
            }
            StatusBadge(text, if (d < 0) Tone.Danger else Tone.Accent)
        }
    }
}

@Composable
fun ForecastBadge(f: Forecast?) {
    if (f == null) return
    val text = when (f.status) {
        ForecastStatus.ORDER_NOW -> "Order now"
        ForecastStatus.ORDER_SOON -> f.orderByDay?.let { "Order ${relativeDayInline(it)}" } ?: "Order soon"
        ForecastStatus.HEALTHY -> "Healthy"
        ForecastStatus.NO_USAGE -> "No recent usage"
        ForecastStatus.LEARNING -> "Learning"
    }
    StatusBadge(text, f.status.tone())
}

/** Horizontal bar: stock on hand vs. par level, with a tick at the minimum level. */
@Composable
fun StockLevelBar(item: Item, modifier: Modifier = Modifier) {
    val scaleMax = max(max(item.targetLevel(), item.quantity), 0.0001)
    val fraction = (item.quantity / scaleMax).toFloat().coerceIn(0f, 1f)
    val minFraction = (item.minQuantity / scaleMax).toFloat().coerceIn(0f, 1f)
    val color = item.stockTone().strong()
    Box(
        modifier.height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50)).background(color))
        }
        if (item.minQuantity > 0 && minFraction > 0.02f && minFraction < 0.98f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(minFraction)) {
                Box(
                    Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                )
            }
        }
    }
}

// ---------- states & inputs ----------

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
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
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(v) },
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotBlank() && parseNum(value) == null,
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
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
    val cs = MaterialTheme.colorScheme
    val amountColor = when {
        mv.type == MovementType.WASTE -> MaterialTheme.status.danger
        mv.change > 0 -> MaterialTheme.status.success
        else -> cs.onSurface
    }
    val details = buildList {
        if (showItemName) add(mv.type.label)
        if (mv.note.isNotBlank()) add(mv.note)
        add(if (showItemName) formatClock(mv.timestamp) else formatTime(mv.timestamp))
    }.joinToString(" · ")
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(mv.type.icon(), mv.type.tone(), 38.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (showItemName) m.itemName else mv.type.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(details, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${mv.change.signed()} ${m.itemUnit}", color = amountColor, style = MaterialTheme.typography.titleSmall)
            Text(
                "→ ${mv.quantityAfter.fmt()}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
