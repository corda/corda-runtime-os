package net.corda.messaging.rocks

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import java.util.concurrent.ConcurrentHashMap
import net.corda.rocks.db.api.StorageManager

class RocksMutableMapImpl <K: Any, V: Any> (
    private val storageManager: StorageManager,
    private val tableName: String,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val valueDeserializer: CordaAvroDeserializer<V>,
) : MutableMap<K, V> {

    private val keysSet = ConcurrentHashMap.newKeySet<K>()

    init {
        storageManager.createTableIfNotExists(tableName)
    }

    override val size: Int
        get() = storageManager.estimateKeys(tableName)

    override fun containsKey(key: K): Boolean {
        return keysSet.contains(key)
    }

    override fun containsValue(value: V): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(key: K): V? {
        val keyBytes = cordaAvroSerializer.serialize(key) ?: throw IllegalArgumentException("")
        val result = storageManager.get(tableName, keyBytes) ?: return null
        return valueDeserializer.deserialize(result)
    }

    override fun isEmpty(): Boolean {
        return keysSet.isEmpty()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = TODO("Not yet implemented")
    override val keys: MutableSet<K>
        get() = keysSet
    override val values: MutableCollection<V>
        get() = TODO("Not yet implemented")

    override fun clear() {
        storageManager.flush(tableName)
    }

    override fun put(key: K, value: V): V? {
        val previousValue = storageManager.get(tableName, toBytes(key))
        storageManager.put(tableName, toBytes(key), toBytes(value))
        keysSet.add(key)
        return fromBytes(previousValue)
    }

    override fun putAll(from: Map<out K, V>) {
        TODO("Not yet implemented")
    }

    override fun remove(key: K): V? {
        val previousVal = storageManager.get(tableName, toBytes(key))
        storageManager.delete(tableName, toBytes(key))
        keysSet.remove(key)
        return fromBytes(previousVal)
    }

    private fun toBytes(obj: Any) : ByteArray {
        return cordaAvroSerializer.serialize(obj) ?: throw IllegalArgumentException("")
    }

    private fun fromBytes(valueBytes: ByteArray?) : V? {
        if (valueBytes == null) return null
        return valueDeserializer.deserialize(valueBytes) ?: throw IllegalArgumentException("")
    }
}