package net.corda.libs.configuration.write

import com.typesafe.config.Config
import jdk.jshell.spi.ExecutionControl
import net.corda.data.config.Configuration
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kafka implementation of the [CordaWriteService]
 * @property topicName the topic configurations will be published to
 */
@Component
class CordaWriteServiceImpl constructor(
    private val topicName: String,
    private val publisher: Publisher<String, Configuration>
) : CordaWriteService {

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
        throw ExecutionControl.NotImplementedException("Not yet implemented")
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
        val records = mutableListOf<Record<String, Configuration>>()
        for (key1 in config.root().keys) {
            val key1Config = config.getConfig(key1)
            for (key2 in key1Config.root().keys) {
                val content = Configuration(key1Config.atKey(key2).toString())
                val record = Record(topicName, "$key1.$key2", content)
                log.info("Producing record: $key1.$key2\t$content")
                records.add(record)
            }
        }

        publisher.publish(records)
    }
}
