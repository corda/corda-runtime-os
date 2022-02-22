package net.corda.messagebus.db.producer.builder

import com.typesafe.config.Config
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CLIENT_ID
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.db.datamodel.CommittedOffsetEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl
import net.corda.messagebus.db.producer.CordaTransactionalDBProducerImpl
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
                    "DB Producer for ${producerConfig.getString(CLIENT_ID)}",
                    listOf(
                        TopicRecordEntry::class.java,
                        CommittedOffsetEntry::class.java,
                        TopicEntry::class.java,
                        TransactionRecordEntry::class.java,
                    )
            )
        )
        return if (isTransactional) {
            CordaTransactionalDBProducerImpl(
                avroSchemaRegistry,
                dbAccess
            )
        } else {
            CordaAtomicDBProducerImpl(
                avroSchemaRegistry,
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
        val pass = dbConfig.getString("pass")

        val dbSource = PostgresDataSourceFactory().create(
            jdbcUrl,
            username,
            pass
        )

        return entityManagerFactoryFactory.create(
            persistenceName,
            entities,
            DbEntityManagerConfiguration(dbSource),
        )
    }
}
