package net.corda.rocks.db.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messaging.api.subscription.data.TopicData
import net.corda.messaging.api.subscription.data.DataProcessor
import net.corda.rocks.db.api.QueryProcessorBuilder
import net.corda.rocks.db.api.StorageManager
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

class RocksMapImpl <K: Any, V: Any> (
    private val tableName: String,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val keyDeserializer: CordaAvroDeserializer<K>,
    private val valueDeserializer: CordaAvroDeserializer<V>,
    private val storageManager: StorageManager,
) : TopicData<K, V> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    init {
        logger.trace { "Initializing table $tableName" }
        storageManager.createTableIfNotExists(tableName)
    }

    private val queryProcessorBuilder: QueryProcessorBuilder<K, V> = QueryProcessorBuilderImpl()

    override val size: Int
        get() = storageManager.estimateKeys(tableName)

    override fun get(key: K): V? {
        logger.trace { "Getting value for key $key from table $tableName" }
        val keyBytes = cordaAvroSerializer.serialize(key) ?: throw IllegalArgumentException()
        val result = storageManager.get(tableName, keyBytes) ?: return null
        return valueDeserializer.deserialize(result)
    }

    override fun clear() {
        logger.trace { "Clearing table $tableName" }
        storageManager.flush(tableName)
    }

    override fun iterate(dataProcessor: DataProcessor<K, V>) {
        val queryProcessor = queryProcessorBuilder.buildQueryProcessor(keyDeserializer, valueDeserializer, dataProcessor, tableName)
        storageManager.iterateAll(tableName, queryProcessor)
    }

    override fun put(key: K, value: V)  {
        logger.trace { "Putting key $key, value: $value" }
        storageManager.put(tableName, toBytes(key), toBytes(value))
    }

    override fun remove(key: K): V? {
        logger.trace { "Removing key $key from table $tableName" }
        val previousVal = storageManager.get(tableName, toBytes(key))
        storageManager.delete(tableName, toBytes(key))
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
