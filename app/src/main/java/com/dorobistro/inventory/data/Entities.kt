package com.dorobistro.inventory.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    /** Days between placing an order and the delivery arriving. Used by the replenishment forecast. */
    @ColumnInfo(defaultValue = "2") val leadTimeDays: Int = 2,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("supplierId"), Index("category")],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val unit: String,
    val quantity: Double = 0.0,
    /** Alert threshold: at or below this the item is "low". */
    val minQuantity: Double = 0.0,
    /** Target level the reorder list tops the item back up to. */
    val parLevel: Double = 0.0,
    val costPerUnit: Double = 0.0,
    val supplierId: Long? = null,
    val expiryEpochDay: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class MovementType(val label: String, val sign: Int) {
    DELIVERY("Delivery", 1),
    USAGE("Usage", -1),
    WASTE("Waste", -1),
    COUNT("Stock count", 0),
    ADJUSTMENT("Adjustment", 0),
}

@Entity(
    tableName = "movements",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index("timestamp")],
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val type: MovementType,
    /** Signed change actually applied to the quantity. */
    val change: Double,
    val quantityAfter: Double,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

data class MovementWithItem(
    @Embedded val movement: StockMovement,
    @ColumnInfo(name = "itemName") val itemName: String,
    @ColumnInfo(name = "itemUnit") val itemUnit: String,
)

fun Item.isOut(): Boolean = quantity <= 0.0
fun Item.isLow(): Boolean = quantity <= minQuantity && (minQuantity > 0.0 || quantity <= 0.0)
fun Item.targetLevel(): Double = if (parLevel > minQuantity) parLevel else max(minQuantity * 2, 1.0)
fun Item.suggestedOrder(): Double = ceil(max(targetLevel() - quantity, 0.0))
fun Item.daysUntilExpiry(today: LocalDate = LocalDate.now()): Long? =
    expiryEpochDay?.let { it - today.toEpochDay() }
fun Item.isExpiringSoon(withinDays: Int = 3): Boolean =
    daysUntilExpiry()?.let { it <= withinDays } ?: false
