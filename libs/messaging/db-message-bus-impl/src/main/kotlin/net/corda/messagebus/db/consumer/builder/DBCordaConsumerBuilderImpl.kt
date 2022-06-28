package net.corda.messagebus.db.consumer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.db.configuration.MessageBusConfigResolver
import net.corda.messagebus.db.consumer.ConsumerGroupFactory
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.EntityManagerFactoryCache
import net.corda.messagebus.db.serialization.CordaDBAvroDeserializerImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Generate a DB-backed [CordaConsumer].
 */
@Component(service = [CordaConsumerBuilder::class])
class DBCordaConsumerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = EntityManagerFactoryCache::class)
    private val entityManagerFactoryCache: EntityManagerFactoryCache,
) : CordaConsumerBuilder {

    private val consumerGroupFactory = ConsumerGroupFactory(entityManagerFactoryCache)

    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val resolvedConfig = resolver.resolve(messageBusConfig, consumerConfig)

        val consumerGroup = consumerGroupFactory.getGroupFor(
            consumerConfig.group,
            resolvedConfig
        )
        val emf = entityManagerFactoryCache.getEmf(
            resolvedConfig.jdbcUrl,
            resolvedConfig.jdbcUser,
            resolvedConfig.jdbcPass
        )

        return DBCordaConsumerImpl(
            resolvedConfig,
            DBAccess(emf),
            consumerGroup,
            CordaDBAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz),
            CordaDBAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz),
            listener
        )
    }
}
