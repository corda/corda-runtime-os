package net.corda.messagebus.db.producer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.db.configuration.MessageBusConfigResolver
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.create
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl
import net.corda.messagebus.db.producer.CordaTransactionalDBProducerImpl
import net.corda.messagebus.db.serialization.CordaDBAvroSerializerImpl
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Builder for a DB Producer.
 */
@Component(service = [CordaProducerBuilder::class])
class DBCordaProducerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = EntityManagerFactoryFactory::class)
    private val entityManagerFactoryFactory: EntityManagerFactoryFactory,
) : CordaProducerBuilder {

    private val writeOffsets: WriteOffsets = WriteOffsets(emptyMap())

    override fun createProducer(producerConfig: ProducerConfig, messageBusConfig: SmartConfig): CordaProducer {
        val isTransactional = producerConfig.transactional
        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val resolvedConfig = resolver.resolve(messageBusConfig, producerConfig)

        val dbAccess = DBAccess(
            entityManagerFactoryFactory.create(
                resolvedConfig,
                "DB Producer for ${producerConfig.clientId}",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                )
            )
        )
        writeOffsets.sync(dbAccess.getMaxOffsetsPerTopicPartition())
        return if (isTransactional) {
            CordaTransactionalDBProducerImpl(
                CordaDBAvroSerializerImpl(avroSchemaRegistry),
                dbAccess,
                writeOffsets
            )
        } else {
            CordaAtomicDBProducerImpl(
                CordaDBAvroSerializerImpl(avroSchemaRegistry),
                dbAccess,
                writeOffsets
            )
        }
    }

}
