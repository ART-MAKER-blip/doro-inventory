package com.dorobistro.inventory.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Predictive replenishment.
 *
 * For every item we learn how fast it is being used from its own stock history, then project forward:
 *
 * 1. **Daily usage**: everything that left the shelf (usage, waste, and shortfalls found during stock
 *    counts) over the last [Forecasting.LOOKBACK_DAYS] complete days. When there are at least two weeks of history,
 *    the last 7 days count for 60% so the forecast reacts quickly to busier or quieter weeks.
 * 2. **Days of stock** = quantity on hand ÷ daily usage, which gives the predicted **stock-out date**.
 * 3. **Order-by date**: the day stock falls to the *reorder point*, i.e. enough to last through the
 *    supplier's lead time plus [Forecasting.SAFETY_DAYS] of safety stock.
 * 4. **Suggested quantity**: enough to cover lead time + [Forecasting.COVER_DAYS] days + safety stock,
 *    minus what will still be on the shelf on the order-by date. If the item is below its minimum level,
 *    the par-level suggestion is used when it is larger.
 */
enum class ForecastStatus(val label: String) {
    ORDER_NOW("Order now"),
    ORDER_SOON("Order soon"),
    HEALTHY("Healthy"),
    NO_USAGE("No recent usage"),
    LEARNING("Learning"),
}

data class Forecast(
    val itemId: Long,
    /** Weighted average units used per day. */
    val dailyUsage: Double,
    /** Last 7 days compared with the weeks before, in percent. Null when there isn't enough history. */
    val trendPercent: Double?,
    /** How many days the current stock lasts. Null when nothing is being used. */
    val daysOfStock: Double?,
    /** Epoch day the item is predicted to run out. */
    val stockOutDay: Long?,
    /** Epoch day an order should be placed to arrive in time. */
    val orderByDay: Long?,
    val leadTimeDays: Int,
    /** Recommended order quantity in the item's unit (0 if none needed). */
    val suggestedQty: Double,
    val status: ForecastStatus,
    /** Days of history the forecast is based on (max [Forecasting.LOOKBACK_DAYS]). */
    val dataDays: Int,
) {
    val needsOrder: Boolean get() = status == ForecastStatus.ORDER_NOW || status == ForecastStatus.ORDER_SOON
}

object Forecasting {
    const val LOOKBACK_DAYS = 28
    const val RECENT_DAYS = 7
    const val COVER_DAYS = 7
    const val SAFETY_DAYS = 2
    const val SOON_DAYS = 3
    const val MIN_DATA_DAYS = 3
    const val DEFAULT_LEAD_TIME = 2

    private const val RECENT_WEIGHT = 0.6

    fun epochDay(ms: Long, zone: ZoneId): Long = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    /** Amount that left the shelf in a movement. Positive count corrections are not treated as negative usage. */
    fun consumed(m: StockMovement): Double = when (m.type) {
        MovementType.USAGE, MovementType.WASTE, MovementType.COUNT -> max(-m.change, 0.0)
        MovementType.DELIVERY, MovementType.ADJUSTMENT -> 0.0
    }

    fun forecastAll(
        items: List<Item>,
        suppliers: List<Supplier>,
        movements: List<StockMovement>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Long, Forecast> {
        val leadTimes = suppliers.associate { it.id to it.leadTimeDays }
        val byItem = movements.groupBy { it.itemId }
        return items.associate { item ->
            val lead = item.supplierId?.let { leadTimes[it] } ?: DEFAULT_LEAD_TIME
            item.id to forecast(item, byItem[item.id].orEmpty(), lead, today, zone)
        }
    }

    /** [movements] must belong to [item]. */
    fun forecast(item: Item, movements: List<StockMovement>, leadTimeDays: Int, today: LocalDate, zone: ZoneId): Forecast {
        val todayEpoch = today.toEpochDay()
        val lead = leadTimeDays.coerceAtLeast(0)
        val firstDay = movements.minOfOrNull { epochDay(it.timestamp, zone) }
        // Only complete days count (today is still in progress), so the window ends yesterday.
        val dataDays = if (firstDay == null) 0 else (todayEpoch - firstDay).toInt().coerceIn(0, LOOKBACK_DAYS)

        var rate = 0.0
        var trend: Double? = null
        if (dataDays > 0) {
            val windowStart = todayEpoch - dataDays
            val recentDays = minOf(RECENT_DAYS, dataDays)
            val recentStart = todayEpoch - recentDays
            var total = 0.0
            var recent = 0.0
            for (m in movements) {
                val d = epochDay(m.timestamp, zone)
                if (d < windowStart || d >= todayEpoch) continue
                val c = consumed(m)
                total += c
                if (d >= recentStart) recent += c
            }
            val windowRate = total / dataDays
            val recentRate = recent / recentDays
            val olderDays = dataDays - recentDays
            if (olderDays >= RECENT_DAYS) {
                val olderRate = (total - recent) / olderDays
                rate = RECENT_WEIGHT * recentRate + (1 - RECENT_WEIGHT) * windowRate
                if (olderRate > 0) trend = (recentRate - olderRate) / olderRate * 100.0
            } else {
                rate = windowRate
            }
        }

        val qty = item.quantity
        var daysOfStock: Double? = null
        var stockOut: Long? = null
        var orderBy: Long? = null
        var forecastQty = 0.0
        if (rate > 0) {
            daysOfStock = qty / rate
            stockOut = todayEpoch + floor(daysOfStock).toLong()
            val reorderPoint = rate * (lead + SAFETY_DAYS)
            val daysUntilOrder = if (qty <= reorderPoint) 0L else floor((qty - reorderPoint) / rate).toLong()
            orderBy = todayEpoch + daysUntilOrder
            val onHandAtOrder = max(qty - rate * daysUntilOrder, 0.0)
            val orderUpTo = rate * (lead + COVER_DAYS + SAFETY_DAYS)
            forecastQty = max(orderUpTo - onHandAtOrder, 0.0)
        }

        val orderByDay = orderBy ?: Long.MAX_VALUE
        val status = when {
            item.isLow() -> ForecastStatus.ORDER_NOW
            dataDays < MIN_DATA_DAYS -> ForecastStatus.LEARNING
            rate <= 0.0 -> ForecastStatus.NO_USAGE
            orderByDay <= todayEpoch -> ForecastStatus.ORDER_NOW
            orderByDay <= todayEpoch + SOON_DAYS -> ForecastStatus.ORDER_SOON
            else -> ForecastStatus.HEALTHY
        }
        val useForecast = rate > 0 && dataDays >= MIN_DATA_DAYS
        val parQty = if (item.isLow()) item.suggestedOrder() else 0.0
        val suggested = roundUp(max(if (useForecast) forecastQty else 0.0, parQty))

        return Forecast(
            itemId = item.id,
            dailyUsage = rate,
            trendPercent = trend,
            daysOfStock = daysOfStock,
            stockOutDay = stockOut,
            orderByDay = orderBy,
            leadTimeDays = lead,
            suggestedQty = suggested,
            status = status,
            dataDays = dataDays,
        )
    }

    /**
     * End-of-day stock levels for the last [days] days plus today (size = days + 1, oldest first),
     * rebuilt from the movement log. Used for the history part of the projection chart.
     */
    fun stockLevels(item: Item, movements: List<StockMovement>, days: Int, today: LocalDate, zone: ZoneId): List<Double> {
        val sorted = movements.sortedWith(compareBy<StockMovement>({ it.timestamp }, { it.id }))
        if (sorted.isEmpty()) return List(days + 1) { item.quantity }
        val first = sorted.first()
        val startLevel = (first.quantityAfter - first.change).coerceAtLeast(0.0)
        val todayEpoch = today.toEpochDay()
        val levels = ArrayList<Double>(days + 1)
        var idx = 0
        var level = startLevel
        for (offset in days downTo 0) {
            val day = todayEpoch - offset
            while (idx < sorted.size && epochDay(sorted[idx].timestamp, zone) <= day) {
                level = sorted[idx].quantityAfter
                idx++
            }
            levels += level
        }
        levels[levels.lastIndex] = item.quantity
        return levels
    }

    private fun roundUp(v: Double): Double = if (v <= 0.0) 0.0 else ceil(v - 1e-9)
}

/** Should this item be on the reorder list (below minimum, or predicted to run short)? */
fun Item.needsOrder(f: Forecast?): Boolean = isLow() || f?.needsOrder == true

/** Quantity to order, combining the forecast with the min/par rule. */
fun Item.recommendedOrder(f: Forecast?): Double {
    val par = if (isLow()) suggestedOrder() else 0.0
    return max(f?.suggestedQty ?: 0.0, par).let { if (it <= 0.0 && isLow()) suggestedOrder() else it }
}
