package com.dorobistro.inventory.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class ForecastTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 18)
    private val item = Item(id = 1, name = "Tomatoes", category = "Produce", unit = "kg", quantity = 10.0, minQuantity = 2.0, parLevel = 20.0)

    private fun move(daysAgo: Long, type: MovementType, change: Double, after: Double) = StockMovement(
        itemId = 1, type = type, change = change, quantityAfter = after,
        timestamp = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    /** Starting stock of 20 ten days ago, then 1 kg used every day (including today). */
    private val steady = listOf(move(10, MovementType.ADJUSTMENT, 20.0, 20.0)) +
        (9 downTo 0).map { move(it.toLong(), MovementType.USAGE, -1.0, 20.0 - (10 - it)) }

    @Test fun noHistoryIsLearning() {
        val f = Forecasting.forecast(item, emptyList(), 2, today, zone)
        assertEquals(ForecastStatus.LEARNING, f.status)
        assertNull(f.stockOutDay)
    }

    @Test fun usageRateUsesCompleteDaysOnly() {
        // 9 kg used on the 10 complete days before today; today's usage is ignored.
        val f = Forecasting.forecast(item, steady, 2, today, zone)
        assertEquals(0.9, f.dailyUsage, 1e-9)
        assertEquals(10, f.dataDays)
    }

    @Test fun healthyWhenStockOutlastsLeadTime() {
        val f = Forecasting.forecast(item, steady, 2, today, zone)
        assertEquals(ForecastStatus.HEALTHY, f.status)
        assertEquals(today.toEpochDay() + 11, f.stockOutDay)
    }

    @Test fun orderNowWhenStockWontLastUntilDelivery() {
        val f = Forecasting.forecast(item.copy(quantity = 3.0), steady, 2, today, zone)
        assertEquals(ForecastStatus.ORDER_NOW, f.status)
        assertEquals(today.toEpochDay(), f.orderByDay)
        // Covers lead time 2 + cover 7 + safety 2 = 11 days at 0.9/day, minus 3 on hand.
        assertEquals(7.0, f.suggestedQty, 1e-9)
    }

    @Test fun belowMinimumAlwaysOrdersAtLeastUpToPar() {
        val f = Forecasting.forecast(item.copy(quantity = 1.0), emptyList(), 2, today, zone)
        assertEquals(ForecastStatus.ORDER_NOW, f.status)
        assertEquals(19.0, f.suggestedQty, 1e-9)
    }

    @Test fun idleItemHasNoUsage() {
        val f = Forecasting.forecast(item, listOf(move(20, MovementType.ADJUSTMENT, 10.0, 10.0)), 2, today, zone)
        assertEquals(ForecastStatus.NO_USAGE, f.status)
    }

    @Test fun stockLevelsAreRebuiltFromHistory() {
        val levels = Forecasting.stockLevels(item, steady, 14, today, zone)
        assertEquals(15, levels.size)
        assertEquals(0.0, levels.first(), 1e-9)
        assertEquals(10.0, levels.last(), 1e-9)
    }

    @Test fun sampleHistoryIsConsistentAndEndsAtCurrentStock() {
        SampleData.specs(1, 2, 3, 4, today).forEachIndexed { i, spec ->
            val it = spec.item.copy(id = i + 1L)
            val history = SampleData.history(spec.copy(item = it), it.id, 28, today, zone, Random(7))
            var level = 0.0
            history.forEach { m ->
                assertEquals("${it.name}: movement chain", level, m.quantityAfter - m.change, 1e-6)
                assertTrue(m.quantityAfter >= 0)
                level = m.quantityAfter
            }
            assertEquals(it.quantity, level, 1e-6)
        }
    }
}
