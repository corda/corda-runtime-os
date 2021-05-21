package net.corda.libs.configuration.write

import com.typesafe.config.Config
import net.corda.data.config.Configuration
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.publisher.factory.createPublisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.publisher.factory.CordaKafkaPublisherFactory
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.reflect.AvroSchema
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Provides the Configuration Server interface.
 */
@Component
class CordaWriteServiceImpl() : CordaWriteService {

    private val publisher = CordaKafkaPublisherFactory(AvroSchemaRegistryImpl()).createPublisher<String, Configuration>(
        PublisherConfig(
            "",
            ""
        ), mapOf()
    )

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Add the properties recorded in [config] to the component configuration.
     *
     * @param key configuration key containing identity, package version and component version
     * @param config typesafe config object
     */
    override fun appendConfiguration(
        key: CordaConfigurationKey,
        config: Config
    ) {
        //not needed yet
    }

    /**
     * Update the component configuration so that it matches [config].
     *
     * @param key configuration key containing identity, package version and component version
     * @param config typesafe config object
     */
    override fun updateConfiguration(
        key: CordaConfigurationKey,
        config: Config
    ) {
        val topicName = config.getString("topicName")
        val amendedConfig = config.withoutPath("topicName")
        for (key1 in amendedConfig.root().keys) {
            val key1Config = amendedConfig.getConfig(key1)
            for (key2 in key1Config.root().keys) {
                val content = Configuration(key1Config.atKey(key2).toString())
                val record = Record(topicName, "$key1.$key2", content)
                log.info("Producing record: $key1.$key2\t$content")
                publisher.publish(record)
            }
        }

    }
}
