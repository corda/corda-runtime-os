package net.corda.messagebus.db.producer.builder

import com.typesafe.config.Config
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.db.persistence.CommittedOffsetEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.TopicEntry
import net.corda.messagebus.db.persistence.TopicRecordEntry
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl
import net.corda.messagebus.db.producer.CordaTransactionalDBProducerImpl
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

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
        val isTransactional = producerConfig.hasPath("instanceId")
        val dbAccess = DBAccess(
            obtainEntityManagerFactory(
                producerConfig,
                entityManagerFactoryFactory,
                    "blah",  // TODO: What's the name here?
                    listOf(TopicRecordEntry::class.java, CommittedOffsetEntry::class.java, TopicEntry::class.java)
            )
        )
        return if (isTransactional) {
            CordaTransactionalDBProducerImpl(
                avroSchemaRegistry,
                TopicServiceImpl(),
                dbAccess
            )
        } else {
            CordaAtomicDBProducerImpl(
                avroSchemaRegistry,
                TopicServiceImpl(),
                dbAccess
            )
        }
    }

    private fun obtainEntityManagerFactory(
        dbConfig: Config,
        entityManagerFactoryFactory: EntityManagerFactoryFactory,
        persistenceName: String,
        entities: List<Class<out Any>>,
    ): EntityManagerFactory {

        val jdbcUrl = dbConfig.getString("jdbc.url")
        val username = dbConfig.getString("user")

        val dbSource = PostgresDataSourceFactory().create(
            jdbcUrl,
            username,
            dbConfig.getString("pass")
        )

        return entityManagerFactoryFactory.create(
            persistenceName,
            entities,
            DbEntityManagerConfiguration(dbSource),
        )
    }
}
