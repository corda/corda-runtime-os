package net.corda.messaging.rocks

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.rocks.db.api.StorageManager

class RocksMapFactoryImpl <K: Any, V: Any>(
    private val topic: String,
    private val storageManager: StorageManager,
    private val serializer: CordaAvroSerializer<Any>,
    private val deserializer: CordaAvroDeserializer<V>,
) : MapFactory<K, V> {

    override fun createMap(): MutableMap<K, V> {
        return RocksMutableMapImpl(storageManager, topic, serializer, deserializer)
    }

    override fun destroyMap(map: MutableMap<K, V>) {
        storageManager.flush(topic)
    }
}
