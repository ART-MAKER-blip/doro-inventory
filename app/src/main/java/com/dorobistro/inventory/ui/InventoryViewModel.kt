package com.dorobistro.inventory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dorobistro.inventory.data.AppDatabase
import com.dorobistro.inventory.data.Backup
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.MovementWithItem
import com.dorobistro.inventory.data.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val items: StateFlow<List<Item>> =
        dao.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val suppliers: StateFlow<List<Supplier>> =
        dao.observeSuppliers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentMovements: StateFlow<List<MovementWithItem>> =
        dao.observeMovements(1_000).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun movementsFor(itemId: Long): Flow<List<MovementWithItem>> = dao.observeMovementsForItem(itemId)

    fun saveItem(item: Item) = viewModelScope.launch {
        if (item.id == 0L) {
            dao.createItem(item)
        } else {
            // Quantity only changes through logged movements, never through the edit form.
            val current = dao.getItem(item.id)
            dao.updateItem(item.copy(quantity = current?.quantity ?: item.quantity, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteItem(item: Item) = viewModelScope.launch { dao.deleteItem(item) }

    /** [amount] is always positive; the movement type decides the direction. COUNT sets an exact quantity. */
    fun adjust(itemId: Long, type: MovementType, amount: Double, note: String = "") = viewModelScope.launch {
        if (type == MovementType.COUNT) dao.setCount(itemId, amount, note)
        else dao.adjustStock(itemId, abs(amount) * type.sign, type, note)
    }

    fun quickAdjust(item: Item, delta: Double) =
        adjust(item.id, if (delta > 0) MovementType.DELIVERY else MovementType.USAGE, abs(delta), "Quick adjust")

    fun applyCounts(counts: Map<Long, Double>) = viewModelScope.launch { dao.applyCounts(counts) }

    fun saveSupplier(supplier: Supplier) = viewModelScope.launch {
        if (supplier.id == 0L) dao.insertSupplier(supplier) else dao.updateSupplier(supplier)
    }

    fun deleteSupplier(supplier: Supplier) = viewModelScope.launch { dao.deleteSupplier(supplier) }

    suspend fun exportJson(): String =
        Backup.toJson(Backup.Data(dao.allSuppliers(), dao.allItems(), dao.allMovements()))

    /** Replaces everything on the device with the backup. Returns the number of items restored. */
    suspend fun importJson(text: String): Int {
        val data = Backup.fromJson(text)
        dao.replaceAll(data.suppliers, data.items, data.movements)
        return data.items.size
    }

    fun loadSampleData() = viewModelScope.launch {
        val produce = dao.insertSupplier(Supplier(name = "Fresh Farms Produce", contactName = "Order desk", phone = "555-0101"))
        val meats = dao.insertSupplier(Supplier(name = "Prime Cuts Meats", contactName = "Sales", phone = "555-0102"))
        val dairy = dao.insertSupplier(Supplier(name = "Valley Dairy Co.", email = "orders@example.com"))
        val dry = dao.insertSupplier(Supplier(name = "Pantry Wholesale", phone = "555-0104"))
        val today = LocalDate.now()
        listOf(
            Item(name = "Roma tomatoes", category = "Produce", unit = "kg", quantity = 4.0, minQuantity = 5.0, parLevel = 15.0, costPerUnit = 2.4, supplierId = produce, expiryEpochDay = today.plusDays(4).toEpochDay()),
            Item(name = "Yellow onions", category = "Produce", unit = "kg", quantity = 12.0, minQuantity = 5.0, parLevel = 20.0, costPerUnit = 1.1, supplierId = produce),
            Item(name = "Fresh basil", category = "Produce", unit = "bunch", quantity = 3.0, minQuantity = 4.0, parLevel = 10.0, costPerUnit = 1.8, supplierId = produce, expiryEpochDay = today.plusDays(2).toEpochDay()),
            Item(name = "Garlic", category = "Produce", unit = "kg", quantity = 2.5, minQuantity = 1.0, parLevel = 4.0, costPerUnit = 4.5, supplierId = produce),
            Item(name = "Chicken thigh", category = "Meat", unit = "kg", quantity = 8.0, minQuantity = 6.0, parLevel = 20.0, costPerUnit = 6.9, supplierId = meats, expiryEpochDay = today.plusDays(3).toEpochDay()),
            Item(name = "Beef tenderloin", category = "Meat", unit = "kg", quantity = 0.0, minQuantity = 3.0, parLevel = 8.0, costPerUnit = 32.0, supplierId = meats),
            Item(name = "Mozzarella", category = "Dairy", unit = "kg", quantity = 5.0, minQuantity = 3.0, parLevel = 10.0, costPerUnit = 9.5, supplierId = dairy, expiryEpochDay = today.plusDays(9).toEpochDay()),
            Item(name = "Parmesan", category = "Dairy", unit = "kg", quantity = 1.5, minQuantity = 2.0, parLevel = 5.0, costPerUnit = 22.0, supplierId = dairy),
            Item(name = "Heavy cream", category = "Dairy", unit = "L", quantity = 6.0, minQuantity = 4.0, parLevel = 12.0, costPerUnit = 4.2, supplierId = dairy, expiryEpochDay = today.plusDays(6).toEpochDay()),
            Item(name = "Extra virgin olive oil", category = "Dry goods", unit = "L", quantity = 9.0, minQuantity = 5.0, parLevel = 15.0, costPerUnit = 11.0, supplierId = dry),
            Item(name = "Flour 00", category = "Dry goods", unit = "kg", quantity = 18.0, minQuantity = 10.0, parLevel = 40.0, costPerUnit = 1.6, supplierId = dry),
            Item(name = "Espresso beans", category = "Beverage", unit = "kg", quantity = 2.0, minQuantity = 3.0, parLevel = 8.0, costPerUnit = 19.0, supplierId = dry),
        ).forEach { dao.createItem(it) }
    }
}
