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

    @Query("SELECT * FROM items WHERE id = :id")
    abstract suspend fun getItem(id: Long): Item?

    @Insert abstract suspend fun insertItem(item: Item): Long
    @Update abstract suspend fun updateItem(item: Item)
    @Delete abstract suspend fun deleteItem(item: Item)

    @Insert abstract suspend fun insertSupplier(supplier: Supplier): Long
    @Update abstract suspend fun updateSupplier(supplier: Supplier)
    @Delete abstract suspend fun deleteSupplier(supplier: Supplier)

    @Insert abstract suspend fun insertMovement(movement: StockMovement): Long

    /** Adds [change] (may be negative) to the item and logs it. Quantity never goes below zero. */
    @Transaction
    open suspend fun adjustStock(itemId: Long, change: Double, type: MovementType, note: String) {
        val item = getItem(itemId) ?: return
        val newQty = (item.quantity + change).coerceAtLeast(0.0)
        val applied = newQty - item.quantity
        if (applied == 0.0) return
        updateItem(item.copy(quantity = newQty, updatedAt = System.currentTimeMillis()))
        insertMovement(StockMovement(itemId = itemId, type = type, change = applied, quantityAfter = newQty, note = note))
    }

    /** Sets the item to a physically counted quantity and logs the correction. */
    @Transaction
    open suspend fun setCount(itemId: Long, counted: Double, note: String) {
        val item = getItem(itemId) ?: return
        val newQty = counted.coerceAtLeast(0.0)
        if (newQty == item.quantity) return
        updateItem(item.copy(quantity = newQty, updatedAt = System.currentTimeMillis()))
        insertMovement(
            StockMovement(itemId = itemId, type = MovementType.COUNT, change = newQty - item.quantity, quantityAfter = newQty, note = note)
        )
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
