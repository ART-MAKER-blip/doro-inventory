package com.dorobistro.inventory.data

import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON backup format so data can be moved between tablets or restored after a reset. */
object Backup {
    private const val FORMAT = "doro-inventory"
    private const val VERSION = 1

    data class Data(
        val suppliers: List<Supplier>,
        val items: List<Item>,
        val movements: List<StockMovement>,
    )

    fun toJson(data: Data): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
        root.put("suppliers", JSONArray().apply {
            data.suppliers.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("name", it.name).put("contactName", it.contactName)
                        .put("phone", it.phone).put("email", it.email).put("notes", it.notes)
                )
            }
        })
        root.put("items", JSONArray().apply {
            data.items.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("name", it.name).put("category", it.category).put("unit", it.unit)
                        .put("quantity", it.quantity).put("minQuantity", it.minQuantity).put("parLevel", it.parLevel)
                        .put("costPerUnit", it.costPerUnit)
                        .put("supplierId", it.supplierId ?: JSONObject.NULL)
                        .put("expiryEpochDay", it.expiryEpochDay ?: JSONObject.NULL)
                        .put("notes", it.notes).put("updatedAt", it.updatedAt)
                )
            }
        })
        root.put("movements", JSONArray().apply {
            data.movements.forEach {
                put(
                    JSONObject()
                        .put("id", it.id).put("itemId", it.itemId).put("type", it.type.name)
                        .put("change", it.change).put("quantityAfter", it.quantityAfter)
                        .put("note", it.note).put("timestamp", it.timestamp)
                )
            }
        })
        return root.toString(2)
    }

    /** @throws IllegalArgumentException if the file is not a D'oro Inventory backup. */
    fun fromJson(text: String): Data {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't valid JSON.")
        }
        require(root.optString("format") == FORMAT) { "This file isn't a D'oro Inventory backup." }

        val suppliers = root.getJSONArray("suppliers").objects().map {
            Supplier(
                id = it.getLong("id"), name = it.getString("name"),
                contactName = it.optString("contactName"), phone = it.optString("phone"),
                email = it.optString("email"), notes = it.optString("notes"),
            )
        }
        val items = root.getJSONArray("items").objects().map {
            Item(
                id = it.getLong("id"), name = it.getString("name"), category = it.optString("category"),
                unit = it.optString("unit"), quantity = it.optDouble("quantity", 0.0),
                minQuantity = it.optDouble("minQuantity", 0.0), parLevel = it.optDouble("parLevel", 0.0),
                costPerUnit = it.optDouble("costPerUnit", 0.0),
                supplierId = it.nullableLong("supplierId"), expiryEpochDay = it.nullableLong("expiryEpochDay"),
                notes = it.optString("notes"), updatedAt = it.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        val movements = root.getJSONArray("movements").objects().map {
            StockMovement(
                id = it.getLong("id"), itemId = it.getLong("itemId"),
                type = runCatching { MovementType.valueOf(it.getString("type")) }.getOrDefault(MovementType.ADJUSTMENT),
                change = it.getDouble("change"), quantityAfter = it.getDouble("quantityAfter"),
                note = it.optString("note"), timestamp = it.getLong("timestamp"),
            )
        }
        return Data(suppliers, items, movements)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
}
