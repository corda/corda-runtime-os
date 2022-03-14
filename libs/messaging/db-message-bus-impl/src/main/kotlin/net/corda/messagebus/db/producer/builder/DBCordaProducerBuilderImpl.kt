package net.corda.messagebus.db.producer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.TRANSACTIONAL_ID
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.create
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl
import net.corda.messagebus.db.producer.CordaTransactionalDBProducerImpl
import net.corda.messagebus.db.serialization.CordaDBAvroSerializerImpl
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
    override fun createProducer(producerConfig: Config): CordaProducer {
        val dbAccess = DBAccess(
            entityManagerFactoryFactory.create(
                producerConfig,
                "test",
//                "DB Producer for ${producerConfig.getString(CLIENT_ID)}",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                )
            )
        )
        return if (producerConfig.hasPath(TRANSACTIONAL_ID)) {
            CordaTransactionalDBProducerImpl(
                CordaDBAvroSerializerImpl(avroSchemaRegistry),
                dbAccess
            )
        } else {
            CordaAtomicDBProducerImpl(
                CordaDBAvroSerializerImpl(avroSchemaRegistry),
                dbAccess
            )
        }
    }

}
