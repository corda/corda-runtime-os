package net.corda.messagebus.db.producer.builder

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.db.persistence.DBWriter
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl
import net.corda.messagebus.db.producer.CordaTransactionalDBProducerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Builder for a Kafka Producer.
 * Initialises producer for transactions if publisherConfig contains an instanceId.
 * Producer uses avro for serialization.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
@Component(service = [CordaProducerBuilder::class])
class DBCordaProducerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : CordaProducerBuilder {
    override fun createProducer(producerConfig: Config): CordaProducer {
        val isTransactional = producerConfig.hasPath("instanceId")
        val dbWriter = DBWriter(
            producerConfig,
            1
        )
        return if (isTransactional) {
            CordaTransactionalDBProducerImpl(
                avroSchemaRegistry,
                TopicServiceImpl(),
                dbWriter
            )
        } else {
            CordaAtomicDBProducerImpl(
                avroSchemaRegistry,
                TopicServiceImpl(),
                dbWriter
            )
        }
    }

    private fun setupDB(config: Config): HikariDataSource {
        val dbSettings = config.getConfig("db")
        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = dbSettings.getString("jdbc")
        hikariConfig.username = dbSettings.getString("user")
        hikariConfig.password = dbSettings.getString("pass")
        hikariConfig.isAutoCommit = false
        hikariConfig.maximumPoolSize = dbSettings.getInt("poolSize")
        return HikariDataSource(hikariConfig)
    }
}
