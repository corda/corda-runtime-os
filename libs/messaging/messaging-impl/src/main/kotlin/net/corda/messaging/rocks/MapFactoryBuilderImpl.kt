package net.corda.messaging.rocks

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.subscription.factory.TopicDataFactory
import net.corda.rocks.db.api.StorageManagerFactory
import net.corda.schema.configuration.ConfigKeys.WORKSPACE_DIR
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [TopicDataFactoryBuilder::class])
class MapFactoryBuilderImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = StorageManagerFactory::class)
    private val storageManagerFactory: StorageManagerFactory,
) : TopicDataFactoryBuilder {

    override fun <K : Any, V : Any> create(
        config: SmartConfig,
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>
    ): TopicDataFactory<K, V> {
        val rocksDBDir = config.getString(WORKSPACE_DIR)
        val useRocksDB = !rocksDBDir.isNullOrBlank()
        val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> {}
        val keyDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, keyClass)
        val valueDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, valueClass)
        return if (useRocksDB) {
            RocksTopicDataFactoryImpl(topic, serializer, keyDeserializer, valueDeserializer, storageManagerFactory.getStorageManger(config))
        } else {
            SimpleTopicDataFactoryImpl()
        }
    }
}
