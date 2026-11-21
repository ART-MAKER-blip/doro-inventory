package com.dorobistro.inventory.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Sample bistro data with a few weeks of believable history, so the forecast and insights
 * screens have something to show straight away.
 */
object SampleData {

    data class Spec(
        val item: Item,
        /** Typical units used per day (today's pace). */
        val dailyUse: Double,
        /** 0.3 means usage has grown about 30% over the history period. */
        val growth: Double = 0.0,
        /** Log some waste every N days (0 = never). */
        val wasteEvery: Int = 0,
        val wasteQty: Double = 0.0,
        /** Round amounts to this step (1.0 for whole units like bunches). */
        val step: Double = 0.1,
    )

    /**
     * Builds [days] days of history for one item that ends exactly at the item's current quantity.
     * Works backwards from today: each day's usage and waste is "un-done", and whenever the stock
     * would have been above the par level that morning, a delivery is inserted instead.
     */
    fun history(spec: Spec, itemId: Long, days: Int, today: LocalDate, zone: ZoneId, random: Random): List<StockMovement> {
        val item = spec.item
        val target = item.targetLevel()
        val low = max(item.minQuantity * 0.6, 0.0)
        val out = ArrayList<StockMovement>()
        var level = item.quantity

        fun ts(daysAgo: Int, hour: Int, minute: Int): Long =
            today.minusDays(daysAgo.toLong()).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

        fun round(v: Double): Double = (v / spec.step).roundToLong() * spec.step

        for (k in 1..days) {
            // Waste at the end of the day.
            if (spec.wasteEvery > 0 && k % spec.wasteEvery == 0 && spec.wasteQty > 0) {
                val w = round(spec.wasteQty * (0.7 + random.nextDouble() * 0.6)).coerceAtLeast(spec.step)
                out += StockMovement(itemId = itemId, type = MovementType.WASTE, change = -w, quantityAfter = clean(level),
                    note = listOf("Spoiled", "Past best-before", "Dropped during prep").random(random), timestamp = ts(k, 22, 10))
                level += w
            }
            // Usage during service. Older days are scaled down when the item is growing.
            val pace = spec.dailyUse * (1 - spec.growth * (k.toDouble() / days)) * (0.75 + random.nextDouble() * 0.5)
            val u = round(pace)
            if (u > 0) {
                out += StockMovement(itemId = itemId, type = MovementType.USAGE, change = -u, quantityAfter = clean(level),
                    note = "Lunch & dinner service", timestamp = ts(k, 21, 30))
                level += u
            }
            // Morning delivery if the shelf would otherwise have been over par.
            if (level > target) {
                val before = round(low + random.nextDouble() * max(item.minQuantity * 0.4, spec.step)).coerceIn(0.0, level)
                val delivered = level - before
                if (delivered > 0) {
                    out += StockMovement(itemId = itemId, type = MovementType.DELIVERY, change = clean(delivered), quantityAfter = clean(level),
                        note = "Invoice #${4100 + random.nextInt(800)}", timestamp = ts(k, 7, 45))
                    level = before
                }
            }
        }
        out += StockMovement(itemId = itemId, type = MovementType.ADJUSTMENT, change = clean(level), quantityAfter = clean(level),
            note = "Starting stock", timestamp = ts(days + 1, 9, 0))
        return out.sortedBy { it.timestamp }
    }

    private fun clean(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** Today's sample items. Supplier ids are filled in by the caller. */
    fun specs(produce: Long, meats: Long, dairy: Long, dry: Long, today: LocalDate = LocalDate.now()): List<Spec> = listOf(
        Spec(Item(name = "Roma tomatoes", category = "Produce", unit = "kg", quantity = 4.0, minQuantity = 5.0, parLevel = 15.0, costPerUnit = 2.4, supplierId = produce, expiryEpochDay = today.plusDays(4).toEpochDay()),
            dailyUse = 1.6, wasteEvery = 6, wasteQty = 0.8),
        Spec(Item(name = "Yellow onions", category = "Produce", unit = "kg", quantity = 12.0, minQuantity = 5.0, parLevel = 20.0, costPerUnit = 1.1, supplierId = produce),
            dailyUse = 1.2),
        Spec(Item(name = "Fresh basil", category = "Produce", unit = "bunch", quantity = 5.0, minQuantity = 4.0, parLevel = 10.0, costPerUnit = 1.8, supplierId = produce, expiryEpochDay = today.plusDays(2).toEpochDay()),
            dailyUse = 1.0, wasteEvery = 4, wasteQty = 1.0, step = 1.0),
        Spec(Item(name = "Garlic", category = "Produce", unit = "kg", quantity = 2.5, minQuantity = 1.0, parLevel = 4.0, costPerUnit = 4.5, supplierId = produce),
            dailyUse = 0.15),
        Spec(Item(name = "Chicken thigh", category = "Meat", unit = "kg", quantity = 8.0, minQuantity = 6.0, parLevel = 20.0, costPerUnit = 6.9, supplierId = meats, expiryEpochDay = today.plusDays(3).toEpochDay()),
            dailyUse = 2.6, growth = 0.5),
        Spec(Item(name = "Beef tenderloin", category = "Meat", unit = "kg", quantity = 0.0, minQuantity = 3.0, parLevel = 8.0, costPerUnit = 32.0, supplierId = meats),
            dailyUse = 0.9),
        Spec(Item(name = "Mozzarella", category = "Dairy", unit = "kg", quantity = 5.0, minQuantity = 3.0, parLevel = 10.0, costPerUnit = 9.5, supplierId = dairy, expiryEpochDay = today.plusDays(9).toEpochDay()),
            dailyUse = 0.8),
        Spec(Item(name = "Parmesan", category = "Dairy", unit = "kg", quantity = 1.5, minQuantity = 2.0, parLevel = 5.0, costPerUnit = 22.0, supplierId = dairy),
            dailyUse = 0.25),
        Spec(Item(name = "Heavy cream", category = "Dairy", unit = "L", quantity = 6.0, minQuantity = 4.0, parLevel = 12.0, costPerUnit = 4.2, supplierId = dairy, expiryEpochDay = today.plusDays(6).toEpochDay()),
            dailyUse = 1.1, wasteEvery = 9, wasteQty = 1.0),
        Spec(Item(name = "Extra virgin olive oil", category = "Dry goods", unit = "L", quantity = 9.0, minQuantity = 5.0, parLevel = 15.0, costPerUnit = 11.0, supplierId = dry),
            dailyUse = 0.6),
        Spec(Item(name = "Flour 00", category = "Dry goods", unit = "kg", quantity = 18.0, minQuantity = 10.0, parLevel = 40.0, costPerUnit = 1.6, supplierId = dry),
            dailyUse = 2.5, growth = 0.15),
        Spec(Item(name = "Espresso beans", category = "Beverage", unit = "kg", quantity = 2.0, minQuantity = 3.0, parLevel = 8.0, costPerUnit = 19.0, supplierId = dry),
            dailyUse = 0.35),
        Spec(Item(name = "Arborio rice", category = "Dry goods", unit = "kg", quantity = 22.0, minQuantity = 5.0, parLevel = 25.0, costPerUnit = 3.2, supplierId = dry),
            dailyUse = 0.5, growth = -0.3),
    )
}
