package com.dorobistro.inventory.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Cost & waste insights for a period (e.g. the last 30 days), compared with the period before it,
 * plus plain-language suggestions. Values use each item's current cost per unit.
 */
data class ItemAmount(val item: Item, val quantity: Double, val value: Double, val share: Double = 0.0)

data class DayValue(val day: LocalDate, val usage: Double, val waste: Double)

enum class TipKind { WASTE, DEMAND_UP, OVERSTOCK, SETUP }

data class Tip(val kind: TipKind, val title: String, val body: String, val itemId: Long? = null)

data class InsightReport(
    val days: Int,
    val usageValue: Double,
    val wasteValue: Double,
    val deliveryValue: Double,
    /** Value of stock found missing during counts (shrinkage). */
    val countLossValue: Double,
    val prevUsageValue: Double,
    val prevWasteValue: Double,
    /** Share of everything consumed that was wasted, 0..1. Null if nothing was consumed. */
    val wasteRate: Double?,
    val daily: List<DayValue>,
    val topWaste: List<ItemAmount>,
    val topUsage: List<ItemAmount>,
    val tips: List<Tip>,
)

object Insights {

    fun build(
        items: List<Item>,
        movements: List<StockMovement>,
        forecasts: Map<Long, Forecast>,
        days: Int,
        today: LocalDate,
        zone: ZoneId,
        money: (Double) -> String,
        num: (Double) -> String,
    ): InsightReport {
        val byId = items.associateBy { it.id }
        val todayEpoch = today.toEpochDay()
        val start = todayEpoch - days + 1
        val prevStart = start - days

        var usage = 0.0; var waste = 0.0; var delivery = 0.0; var countLoss = 0.0
        var prevUsage = 0.0; var prevWaste = 0.0
        val dailyUsage = DoubleArray(days)
        val dailyWaste = DoubleArray(days)
        val usedQty = HashMap<Long, Double>()
        val wastedQty = HashMap<Long, Double>()

        for (m in movements) {
            val item = byId[m.itemId] ?: continue
            val d = Forecasting.epochDay(m.timestamp, zone)
            if (d > todayEpoch || d < prevStart) continue
            val qty = -m.change
            val value = qty * item.costPerUnit
            if (d < start) {
                when (m.type) {
                    MovementType.USAGE -> prevUsage += value
                    MovementType.WASTE -> prevWaste += value
                    else -> {}
                }
                continue
            }
            val i = (d - start).toInt()
            when (m.type) {
                MovementType.USAGE -> {
                    usage += value; dailyUsage[i] += value
                    usedQty[item.id] = (usedQty[item.id] ?: 0.0) + qty
                }
                MovementType.WASTE -> {
                    waste += value; dailyWaste[i] += value
                    wastedQty[item.id] = (wastedQty[item.id] ?: 0.0) + qty
                }
                MovementType.DELIVERY -> delivery += m.change * item.costPerUnit
                MovementType.COUNT -> if (m.change < 0) countLoss += value
                MovementType.ADJUSTMENT -> {}
            }
        }

        val daily = (0 until days).map { DayValue(LocalDate.ofEpochDay(start + it), dailyUsage[it], dailyWaste[it]) }
        val topWaste = wastedQty.mapNotNull { (id, q) ->
            val item = byId[id] ?: return@mapNotNull null
            val used = usedQty[id] ?: 0.0
            ItemAmount(item, q, q * item.costPerUnit, if (used + q > 0) q / (used + q) else 0.0)
        }.sortedWith(compareByDescending<ItemAmount> { it.value }.thenByDescending { it.quantity }).take(5)
        val topUsage = usedQty.mapNotNull { (id, q) ->
            val item = byId[id] ?: return@mapNotNull null
            ItemAmount(item, q, q * item.costPerUnit)
        }.sortedWith(compareByDescending<ItemAmount> { it.value }.thenByDescending { it.quantity }).take(5)

        val consumed = usage + waste
        val tips = buildTips(items, usedQty, wastedQty, forecasts, money, num)

        return InsightReport(
            days = days,
            usageValue = usage,
            wasteValue = waste,
            deliveryValue = delivery,
            countLossValue = countLoss,
            prevUsageValue = prevUsage,
            prevWasteValue = prevWaste,
            wasteRate = if (consumed > 0) waste / consumed else null,
            daily = daily,
            topWaste = topWaste,
            topUsage = topUsage,
            tips = tips,
        )
    }

    private fun buildTips(
        items: List<Item>,
        usedQty: Map<Long, Double>,
        wastedQty: Map<Long, Double>,
        forecasts: Map<Long, Forecast>,
        money: (Double) -> String,
        num: (Double) -> String,
    ): List<Tip> {
        val tips = mutableListOf<Tip>()

        // 1. High waste: suggest a lower par level.
        items.mapNotNull { item ->
            val w = wastedQty[item.id] ?: return@mapNotNull null
            val used = usedQty[item.id] ?: 0.0
            val share = w / (w + used)
            if (share < 0.12 || w <= 0) null else Triple(item, w, share)
        }.sortedByDescending { it.second * it.first.costPerUnit }.take(3).forEach { (item, w, share) ->
            val pct = (share * 100).toInt()
            val body = if (item.parLevel > 0) {
                val newPar = ceil(item.parLevel * (1 - share)).coerceAtLeast(item.minQuantity)
                "$pct% of what you go through is thrown away (${num(w)} ${item.unit}" +
                    (if (item.costPerUnit > 0) ", ${money(w * item.costPerUnit)}" else "") +
                    "). Try lowering the par level from ${num(item.parLevel)} to ${num(newPar)} ${item.unit}, or order smaller amounts more often."
            } else {
                "$pct% of what you go through is thrown away (${num(w)} ${item.unit}). Order smaller amounts more often."
            }
            tips += Tip(TipKind.WASTE, "Reduce waste: ${item.name}", body, item.id)
        }

        // 2. Demand rising faster than the par level allows.
        items.mapNotNull { item ->
            val f = forecasts[item.id] ?: return@mapNotNull null
            val trend = f.trendPercent ?: return@mapNotNull null
            val needed = ceil(f.dailyUsage * (f.leadTimeDays + Forecasting.COVER_DAYS + Forecasting.SAFETY_DAYS))
            if (trend >= 20 && item.parLevel > 0 && item.parLevel < needed) Triple(item, f, needed) else null
        }.sortedByDescending { it.second.trendPercent }.take(2).forEach { (item, f, needed) ->
            tips += Tip(
                TipKind.DEMAND_UP,
                "Demand is rising: ${item.name}",
                "Usage is up ${f.trendPercent!!.toInt()}% this week (about ${num(f.dailyUsage)} ${item.unit}/day). " +
                    "The par level of ${num(item.parLevel)} ${item.unit} no longer covers a full week, so consider raising it to ${num(needed)} ${item.unit}.",
                item.id,
            )
        }

        // 3. Overstock: money tied up in slow-moving items.
        items.mapNotNull { item ->
            val f = forecasts[item.id] ?: return@mapNotNull null
            val d = f.daysOfStock ?: return@mapNotNull null
            if (f.dataDays >= 14 && d > 30 && item.quantity * item.costPerUnit > 0) item to d else null
        }.sortedByDescending { it.first.quantity * it.first.costPerUnit }.take(2).forEach { (item, d) ->
            tips += Tip(
                TipKind.OVERSTOCK,
                "Overstocked: ${item.name}",
                "At the current pace, today's ${num(item.quantity)} ${item.unit} lasts about ${d.toInt()} days " +
                    "and ties up ${money(item.quantity * item.costPerUnit)}. Skip the next order or lower the par level.",
                item.id,
            )
        }

        // 4. Setup gaps that make reports and forecasts less accurate.
        val noCost = items.count { it.costPerUnit <= 0 }
        if (noCost > 0) {
            tips += Tip(
                TipKind.SETUP,
                "Add missing costs",
                "$noCost item${if (noCost == 1) " has" else "s have"} no cost per unit, so they're left out of the value and waste totals.",
            )
        }
        val noSupplier = items.count { it.supplierId == null }
        if (noSupplier > 0) {
            tips += Tip(
                TipKind.SETUP,
                "Link items to suppliers",
                "$noSupplier item${if (noSupplier == 1) " isn't" else "s aren't"} linked to a supplier. Forecasts for them assume a ${Forecasting.DEFAULT_LEAD_TIME}-day lead time.",
            )
        }
        return tips
    }
}
