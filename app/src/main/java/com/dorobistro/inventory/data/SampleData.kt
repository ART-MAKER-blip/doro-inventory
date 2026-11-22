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

    /** Database ids of the sample suppliers, filled in by the caller. */
    data class SupplierIds(
        val produce: Long, val meats: Long, val seafood: Long,
        val dairy: Long, val dry: Long, val coffee: Long,
    )

    /** Days from ordering to delivery for each sample supplier. */
    object Lead {
        const val PRODUCE = 1
        const val MEATS = 1
        const val SEAFOOD = 1
        const val DAIRY = 2
        const val DRY = 3
        const val COFFEE = 4
    }

    /**
     * Sample stock for a Filipino bistro in Metro Manila (silog breakfasts, adobo, sinigang, kare-kare,
     * sisig, pasta, coffee and shakes). Costs are typical 2026 wholesale / palengke prices in pesos per unit.
     *
     * Stock levels are worked out from each item's daily usage and its supplier's lead time, the same
     * way a kitchen would set them: the minimum covers the lead time plus a 2-day buffer, the par level
     * covers another week on top. [daysLeft] says how many days of stock are on the shelf today; most
     * items are comfortably stocked, a few are deliberately low so the reorder and forecast screens
     * have something to show.
     */
    fun specs(s: SupplierIds, today: LocalDate = LocalDate.now()): List<Spec> {
        val out = ArrayList<Spec>()
        fun add(
            name: String, category: String, unit: String, cost: Double, supplier: Long, lead: Int,
            use: Double, daysLeft: Double = lead + 6.0 + name.length % 4, step: Double = 0.1, expiresIn: Long? = null,
            growth: Double = 0.0, wasteEvery: Int = 0, wasteQty: Double = 0.0,
        ) {
            fun r(v: Double) = clean((v / step).roundToLong() * step)
            val min = max(r(use * (lead + 2)), step)
            val par = max(r(use * (lead + 2 + 7)), min + step)
            val qty = r(use * daysLeft).coerceAtMost(par)
            out += Spec(
                Item(name = name, category = category, unit = unit, quantity = qty, minQuantity = min, parLevel = par,
                    costPerUnit = cost, supplierId = supplier, expiryEpochDay = expiresIn?.let { today.plusDays(it).toEpochDay() }),
                dailyUse = use, growth = growth, wasteEvery = wasteEvery, wasteQty = wasteQty, step = step,
            )
        }
        val produce = "Produce"; val meat = "Meat"; val seafood = "Seafood"; val dairy = "Dairy & chilled"
        val dry = "Dry goods"; val sauces = "Sauces & condiments"; val bev = "Beverage"
        val pl = Lead.PRODUCE; val ml = Lead.MEATS; val sl = Lead.SEAFOOD; val dl = Lead.DAIRY; val gl = Lead.DRY; val cl = Lead.COFFEE

        // Produce (palengke, delivered daily)
        add("Tomatoes", produce, "kg", 90.0, s.produce, pl, use = 2.0, daysLeft = 2.5, expiresIn = 4, wasteEvery = 5, wasteQty = 0.6)
        add("Red onions", produce, "kg", 160.0, s.produce, pl, use = 1.5)
        add("White onions", produce, "kg", 140.0, s.produce, pl, use = 0.8)
        add("Garlic", produce, "kg", 160.0, s.produce, pl, use = 1.2, growth = 0.2)
        add("Ginger", produce, "kg", 180.0, s.produce, pl, use = 0.3)
        add("Kangkong", produce, "bundle", 25.0, s.produce, pl, use = 4.0, daysLeft = 5.0, step = 1.0, expiresIn = 1, wasteEvery = 3, wasteQty = 2.0)
        add("Sitaw (string beans)", produce, "bundle", 40.0, s.produce, pl, use = 3.0, step = 1.0, expiresIn = 3, wasteEvery = 6, wasteQty = 1.0)
        add("Talong (eggplant)", produce, "kg", 100.0, s.produce, pl, use = 1.0, expiresIn = 4, wasteEvery = 8, wasteQty = 0.4)
        add("Pechay", produce, "bundle", 30.0, s.produce, pl, use = 3.0, step = 1.0, expiresIn = 2, wasteEvery = 4, wasteQty = 1.0)
        add("Calamansi", produce, "kg", 120.0, s.produce, pl, use = 1.0, expiresIn = 6, wasteEvery = 7, wasteQty = 0.3)
        add("Siling labuyo", produce, "kg", 400.0, s.produce, pl, use = 0.1, step = 0.05)
        add("Romaine lettuce", produce, "kg", 250.0, s.produce, pl, use = 0.6, expiresIn = 2, wasteEvery = 4, wasteQty = 0.3)
        add("Potatoes", produce, "kg", 110.0, s.produce, pl, use = 1.2)
        add("Carrots", produce, "kg", 110.0, s.produce, pl, use = 0.8)
        add("Ripe mangoes", produce, "kg", 160.0, s.produce, pl, use = 1.0, daysLeft = 4.5, expiresIn = 3, growth = 0.4, wasteEvery = 5, wasteQty = 0.5)

        // Meat
        add("Pork belly (liempo)", meat, "kg", 380.0, s.meats, ml, use = 3.5, growth = 0.2, expiresIn = 4)
        add("Pork kasim", meat, "kg", 330.0, s.meats, ml, use = 2.0, expiresIn = 4)
        add("Pork maskara & ears (sisig)", meat, "kg", 220.0, s.meats, ml, use = 1.2, daysLeft = 1.0, expiresIn = 2)
        add("Chicken leg quarters", meat, "kg", 210.0, s.meats, ml, use = 4.0, expiresIn = 3)
        add("Beef sirloin (tapa & salpicao)", meat, "kg", 560.0, s.meats, ml, use = 1.2, expiresIn = 5)
        add("Oxtail (kare-kare)", meat, "kg", 650.0, s.meats, ml, use = 0.8, daysLeft = 0.0)
        add("Longganisa", meat, "kg", 320.0, s.meats, ml, use = 1.0, expiresIn = 10)
        add("Bacon", meat, "kg", 550.0, s.meats, ml, use = 0.5, expiresIn = 14)

        // Seafood (fish port suki)
        add("Bangus belly (boneless)", seafood, "kg", 380.0, s.seafood, sl, use = 1.2, expiresIn = 2, wasteEvery = 9, wasteQty = 0.4)
        add("Shrimp, medium", seafood, "kg", 550.0, s.seafood, sl, use = 0.8, daysLeft = 3.5, growth = 0.3, expiresIn = 2)
        add("Squid", seafood, "kg", 380.0, s.seafood, sl, use = 0.5, expiresIn = 2)

        // Dairy & chilled
        add("Eggs", dairy, "tray (30)", 250.0, s.meats, ml, use = 2.0, step = 1.0, wasteEvery = 10, wasteQty = 0.2)
        add("Fresh milk", dairy, "L", 100.0, s.dairy, dl, use = 4.0, growth = 0.3, expiresIn = 5, wasteEvery = 8, wasteQty = 1.0)
        add("All-purpose cream", dairy, "L", 280.0, s.dairy, dl, use = 1.0, expiresIn = 20)
        add("Butter", dairy, "kg", 550.0, s.dairy, dl, use = 0.4, expiresIn = 30)
        add("Quickmelt cheese", dairy, "kg", 450.0, s.dairy, dl, use = 0.3, expiresIn = 25)
        add("Parmesan", dairy, "kg", 1400.0, s.dairy, dl, use = 0.15, daysLeft = 3.0, step = 0.05)
        add("Condensed milk", dairy, "can", 60.0, s.dairy, dl, use = 3.0, step = 1.0)
        add("Evaporated milk", dairy, "can", 45.0, s.dairy, dl, use = 2.0, step = 1.0)

        // Dry goods
        add("Rice (dinorado)", dry, "kg", 60.0, s.dry, gl, use = 12.0, growth = 0.1, step = 0.5)
        add("Spaghetti", dry, "kg", 130.0, s.dry, gl, use = 1.5)
        add("Cooking oil", dry, "L", 100.0, s.dry, gl, use = 2.5, daysLeft = 6.0)
        add("White sugar", dry, "kg", 90.0, s.dry, gl, use = 1.5)
        add("Peanut butter", dry, "kg", 300.0, s.dry, gl, use = 0.2)
        add("Sinigang mix (tamarind)", dry, "pack", 25.0, s.dry, gl, use = 3.0, step = 1.0)
        add("Extra virgin olive oil", dry, "L", 650.0, s.dry, gl, use = 0.3, growth = -0.3)

        // Sauces & condiments
        add("Soy sauce", sauces, "L", 60.0, s.dry, gl, use = 0.8)
        add("Cane vinegar", sauces, "L", 50.0, s.dry, gl, use = 0.7)
        add("Patis (fish sauce)", sauces, "L", 80.0, s.dry, gl, use = 0.2)
        add("Bagoong alamang", sauces, "kg", 250.0, s.dry, gl, use = 0.2, expiresIn = 30)

        // Beverage
        add("Coffee beans (Benguet arabica)", bev, "kg", 900.0, s.coffee, cl, use = 0.7, daysLeft = 4.0, growth = 0.25)
        add("Bottled water 500 mL", bev, "case (24)", 200.0, s.dry, gl, use = 1.0, step = 1.0)
        add("Soft drinks (cans)", bev, "case (24)", 900.0, s.dry, gl, use = 0.5, step = 1.0)
        add("Tube ice", bev, "bag (5 kg)", 50.0, s.produce, pl, use = 6.0, growth = 0.2, step = 1.0)
        return out
    }
}
