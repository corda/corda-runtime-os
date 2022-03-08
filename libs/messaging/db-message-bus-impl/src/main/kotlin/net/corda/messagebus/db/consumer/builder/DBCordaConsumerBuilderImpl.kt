package net.corda.messagebus.db.consumer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
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
@Component(service = [MessageBusConsumerBuilder::class])
class DBCordaConsumerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) : MessageBusConsumerBuilder {

    private val consumerGroupFactory = ConsumerGroupFactory()

    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        val dbAccess = DBAccess(
            entityManagerFactoryFactory.create(
                consumerConfig,
                "DB Consumer for ${consumerConfig.getString(ConfigProperties.CLIENT_ID)}",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                )
            )
        )

        val consumerGroup = if (consumerConfig.hasPath(ConfigProperties.GROUP_ID)) {
            consumerGroupFactory.getGroupFor(
                consumerConfig.getString(ConfigProperties.GROUP_ID),
                dbAccess
            )
        } else {
            null
        }

        return DBCordaConsumerImpl(
            consumerConfig,
            dbAccess,
            consumerGroup,
            CordaDBAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz),
            CordaDBAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz),
            listener
        )
    }
}
