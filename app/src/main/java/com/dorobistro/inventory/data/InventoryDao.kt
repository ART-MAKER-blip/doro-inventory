package com.dorobistro.inventory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InventoryDao {

    @Query("SELECT * FROM items ORDER BY category COLLATE NOCASE, name COLLATE NOCASE")
    abstract fun observeItems(): Flow<List<Item>>

    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE")
    abstract fun observeSuppliers(): Flow<List<Supplier>>

    @Query(
        """SELECT movements.*, items.name AS itemName, items.unit AS itemUnit
           FROM movements INNER JOIN items ON items.id = movements.itemId
           ORDER BY movements.timestamp DESC, movements.id DESC LIMIT :limit"""
    )
    abstract fun observeMovements(limit: Int): Flow<List<MovementWithItem>>

    @Query(
        """SELECT movements.*, items.name AS itemName, items.unit AS itemUnit
           FROM movements INNER JOIN items ON items.id = movements.itemId
           WHERE movements.itemId = :itemId
           ORDER BY movements.timestamp DESC, movements.id DESC"""
    )
    abstract fun observeMovementsForItem(itemId: Long): Flow<List<MovementWithItem>>

    /** Raw movements since [since] (ms). Feeds the replenishment forecast and the insights report. */
    @Query("SELECT * FROM movements WHERE timestamp >= :since ORDER BY timestamp, id")
    abstract fun observeMovementsSince(since: Long): Flow<List<StockMovement>>

    @Query("SELECT * FROM items WHERE id = :id")
    abstract suspend fun getItem(id: Long): Item?

    @Insert abstract suspend fun insertItem(item: Item): Long
    @Update abstract suspend fun updateItem(item: Item)
    @Delete abstract suspend fun deleteItem(item: Item)

    @Insert abstract suspend fun insertSupplier(supplier: Supplier): Long
    @Update abstract suspend fun updateSupplier(supplier: Supplier)
    @Delete abstract suspend fun deleteSupplier(supplier: Supplier)

    @Insert abstract suspend fun insertMovement(movement: StockMovement): Long

    @Query("SELECT * FROM movements WHERE id = :id")
    abstract suspend fun getMovement(id: Long): StockMovement?

    @Query("SELECT id FROM movements WHERE itemId = :itemId ORDER BY timestamp DESC, id DESC LIMIT 1")
    abstract suspend fun latestMovementId(itemId: Long): Long?

    @Query("DELETE FROM movements WHERE id = :id")
    abstract suspend fun deleteMovementById(id: Long)

    /**
     * Adds [change] (may be negative) to the item and logs it. Quantity never goes below zero.
     * Returns the id of the logged movement, or null if nothing changed.
     */
    @Transaction
    open suspend fun adjustStock(itemId: Long, change: Double, type: MovementType, note: String): Long? {
        val item = getItem(itemId) ?: return null
        val newQty = (item.quantity + change).coerceAtLeast(0.0)
        val applied = newQty - item.quantity
        if (applied == 0.0) return null
        updateItem(item.copy(quantity = newQty, updatedAt = System.currentTimeMillis()))
        return insertMovement(StockMovement(itemId = itemId, type = type, change = applied, quantityAfter = newQty, note = note))
    }

    /** Sets the item to a physically counted quantity and logs the correction. Returns the movement id. */
    @Transaction
    open suspend fun setCount(itemId: Long, counted: Double, note: String): Long? {
        val item = getItem(itemId) ?: return null
        val newQty = counted.coerceAtLeast(0.0)
        if (newQty == item.quantity) return null
        updateItem(item.copy(quantity = newQty, updatedAt = System.currentTimeMillis()))
        return insertMovement(
            StockMovement(itemId = itemId, type = MovementType.COUNT, change = newQty - item.quantity, quantityAfter = newQty, note = note)
        )
    }

    /**
     * Reverses a movement, but only if it is still the item's most recent one (so history stays consistent).
     * Returns true if it was undone.
     */
    @Transaction
    open suspend fun undoMovement(movementId: Long): Boolean {
        val m = getMovement(movementId) ?: return false
        if (latestMovementId(m.itemId) != movementId) return false
        val item = getItem(m.itemId) ?: return false
        val restored = (m.quantityAfter - m.change).coerceAtLeast(0.0)
        updateItem(item.copy(quantity = restored, updatedAt = System.currentTimeMillis()))
        deleteMovementById(movementId)
        return true
    }

    @Transaction
    open suspend fun applyCounts(counts: Map<Long, Double>) {
        counts.forEach { (id, qty) -> setCount(id, qty, "Stock count") }
    }

    @Transaction
    open suspend fun createItem(item: Item): Long {
        val id = insertItem(item)
        if (item.quantity > 0) {
            insertMovement(
                StockMovement(itemId = id, type = MovementType.ADJUSTMENT, change = item.quantity, quantityAfter = item.quantity, note = "Starting stock")
            )
        }
        return id
    }

    // --- Backup / restore ---
    @Query("SELECT * FROM items") abstract suspend fun allItems(): List<Item>
    @Query("SELECT * FROM suppliers") abstract suspend fun allSuppliers(): List<Supplier>
    @Query("SELECT * FROM movements") abstract suspend fun allMovements(): List<StockMovement>
    @Query("DELETE FROM movements") abstract suspend fun clearMovements()
    @Query("DELETE FROM items") abstract suspend fun clearItems()
    @Query("DELETE FROM suppliers") abstract suspend fun clearSuppliers()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSuppliers(list: List<Supplier>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertItems(list: List<Item>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertMovements(list: List<StockMovement>)

    @Transaction
    open suspend fun replaceAll(suppliers: List<Supplier>, items: List<Item>, movements: List<StockMovement>) {
        clearMovements()
        clearItems()
        clearSuppliers()
        insertSuppliers(suppliers)
        insertItems(items)
        insertMovements(movements)
    }
}
