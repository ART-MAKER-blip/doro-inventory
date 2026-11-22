package com.dorobistro.inventory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dorobistro.inventory.data.AppDatabase
import com.dorobistro.inventory.data.Backup
import com.dorobistro.inventory.data.Forecast
import com.dorobistro.inventory.data.Forecasting
import com.dorobistro.inventory.data.Item
import com.dorobistro.inventory.data.MovementType
import com.dorobistro.inventory.data.MovementWithItem
import com.dorobistro.inventory.data.SampleData
import com.dorobistro.inventory.data.StockMovement
import com.dorobistro.inventory.data.Supplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val items: StateFlow<List<Item>> =
        dao.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val suppliers: StateFlow<List<Supplier>> =
        dao.observeSuppliers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentMovements: StateFlow<List<MovementWithItem>> =
        dao.observeMovements(1_000).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 60 days of raw movements: enough for the 28-day forecast and a 30-vs-30-day insights comparison. */
    val windowMovements: StateFlow<List<StockMovement>> =
        dao.observeMovementsSince(System.currentTimeMillis() - WINDOW_DAYS * DAY_MS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Predictive replenishment forecast per item id. Recalculated whenever stock, suppliers or history change. */
    val forecasts: StateFlow<Map<Long, Forecast>> =
        combine(items, suppliers, windowMovements) { items, suppliers, movements ->
            Forecasting.forecastAll(items, suppliers, movements, LocalDate.now(), ZoneId.systemDefault())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    /**
     * [amount] is always positive; the movement type decides the direction. COUNT sets an exact quantity.
     * [onDone] receives the new movement's id (null if nothing changed), e.g. to offer "Undo".
     */
    fun adjust(itemId: Long, type: MovementType, amount: Double, note: String = "", onDone: ((Long?) -> Unit)? = null) =
        viewModelScope.launch {
            val id = if (type == MovementType.COUNT) dao.setCount(itemId, amount, note)
            else dao.adjustStock(itemId, abs(amount) * type.sign, type, note)
            onDone?.invoke(id)
        }

    fun quickAdjust(item: Item, delta: Double, onDone: ((Long?) -> Unit)? = null) =
        adjust(item.id, if (delta > 0) MovementType.DELIVERY else MovementType.USAGE, abs(delta), "Quick adjust", onDone)

    fun undo(movementId: Long, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(dao.undoMovement(movementId))
    }

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

    /** Loads sample suppliers and items with four weeks of history, so forecasts and insights work right away. */
    fun loadSampleData() = viewModelScope.launch {
        val ids = SampleData.SupplierIds(
            produce = dao.insertSupplier(Supplier(name = "Suki gulayan (Balintawak Market)", leadTimeDays = SampleData.Lead.PRODUCE, notes = "Vegetables, fruit, tube ice. Delivers 6am daily; order by 8pm the night before.")),
            meats = dao.insertSupplier(Supplier(name = "Suki butcher (Farmers Market Cubao)", leadTimeDays = SampleData.Lead.MEATS, notes = "Pork, chicken, beef, eggs. Delivers daily except Sunday.")),
            seafood = dao.insertSupplier(Supplier(name = "Navotas Fish Port suki", leadTimeDays = SampleData.Lead.SEAFOOD, notes = "Bangus, shrimp, squid. Early-morning delivery.")),
            dairy = dao.insertSupplier(Supplier(name = "Chilled & dairy distributor", leadTimeDays = SampleData.Lead.DAIRY, notes = "Milk, cream, butter, cheese, canned milk.")),
            dry = dao.insertSupplier(Supplier(name = "Puregold Price Club (wholesale)", leadTimeDays = SampleData.Lead.DRY, notes = "Rice, oil, condiments, drinks. Pickup run Tuesday & Friday.")),
            coffee = dao.insertSupplier(Supplier(name = "Benguet coffee roaster", leadTimeDays = SampleData.Lead.COFFEE, notes = "Arabica beans shipped by courier from Baguio.")),
        )
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val random = Random(42)
        SampleData.specs(ids, today).forEach { spec ->
            val id = dao.insertItem(spec.item)
            dao.insertMovements(SampleData.history(spec, id, SAMPLE_DAYS, today, zone, random))
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val WINDOW_DAYS = 60
        const val SAMPLE_DAYS = 28
    }
}
