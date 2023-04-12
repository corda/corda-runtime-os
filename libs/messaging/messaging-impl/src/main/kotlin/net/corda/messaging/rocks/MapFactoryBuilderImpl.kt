package net.corda.messaging.rocks

import net.corda.data.CordaAvroSerializationFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.rocks.db.api.StorageManager
import net.corda.schema.configuration.ConfigKeys.WORKSPACE_DIR
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MapFactoryBuilder::class])
class MapFactoryBuilderImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : MapFactoryBuilder {

    override fun <K : Any, V : Any> create(
        storageManager: StorageManager,
        config: SmartConfig,
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>,
    ): MapFactory<K, V> {
        val rocksDBDir = config.getString(WORKSPACE_DIR)
        val useRocksDB = !rocksDBDir.isNullOrBlank()
        val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> {}
        val valueDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, valueClass)
        return if (useRocksDB) {
            RocksMapFactoryImpl(topic, storageManager, serializer, valueDeserializer)
        } else {
            SimpleMapFactoryImpl()
        }
    }

    override fun <K : Any, V : Any> createSimpleMapFactory(): MapFactory<K, V> {
        return SimpleMapFactoryImpl()
    }
}
