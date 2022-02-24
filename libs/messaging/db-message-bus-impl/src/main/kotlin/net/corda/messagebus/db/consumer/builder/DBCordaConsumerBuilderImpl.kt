package net.corda.messagebus.db.consumer.builder

import com.typesafe.config.Config
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messagebus.db.consumer.ConsumerGroupFactory
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl
import net.corda.messagebus.db.datamodel.CommittedOffsetEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.serialization.CordaDBAvroDeserializerImpl
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

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
            obtainEntityManagerFactory(
                consumerConfig,
                entityManagerFactoryFactory,
                "DB Consumer for ${consumerConfig.getString(ConfigProperties.CLIENT_ID)}",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedOffsetEntry::class.java,
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
