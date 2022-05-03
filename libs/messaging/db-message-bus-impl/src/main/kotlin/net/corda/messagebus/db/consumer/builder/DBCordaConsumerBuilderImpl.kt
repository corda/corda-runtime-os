package net.corda.messagebus.db.consumer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.db.configuration.MessageBusConfigResolver
import net.corda.messagebus.db.consumer.ConsumerGroupFactory
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.create
import net.corda.messagebus.db.serialization.CordaDBAvroDeserializerImpl
import net.corda.orm.EntityManagerFactoryFactory
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
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) : CordaConsumerBuilder {

    private val consumerGroupFactory = ConsumerGroupFactory(entityManagerFactoryFactory)

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

        val dbAccess = DBAccess(
            entityManagerFactoryFactory.create(
                resolvedConfig,
                "DB Consumer for ${consumerConfig.clientId}",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                )
            )
        )

        val consumerGroup = consumerGroupFactory.getGroupFor(
            consumerConfig.group,
            resolvedConfig
        )

        return DBCordaConsumerImpl(
            resolvedConfig,
            dbAccess,
            consumerGroup,
            CordaDBAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz),
            CordaDBAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz),
            listener
        )
    }
}
