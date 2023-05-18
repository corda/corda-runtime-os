package net.corda.messaging.rocks

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messaging.api.subscription.data.TopicData
import net.corda.messaging.subscription.factory.TopicDataFactory
import net.corda.rocks.db.api.StorageManager
import net.corda.rocks.db.impl.RocksMapImpl

class RocksTopicDataFactoryImpl <K: Any, V: Any>(
    private val table: String,
    private val serializer: CordaAvroSerializer<Any>,
    private val keyDeserializer: CordaAvroDeserializer<K>,
    private val valueDeserializer: CordaAvroDeserializer<V>,
    private val storageManager: StorageManager,
) : TopicDataFactory<K, V> {

    override fun create(): TopicData<K, V> {
        return RocksMapImpl(table, serializer, keyDeserializer, valueDeserializer, storageManager)
    }

    override fun destroy(topicData: TopicData<K, V>) {
        storageManager.flush(table)
    }
}
