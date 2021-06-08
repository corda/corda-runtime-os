package net.corda.libs.configuration.write.kafka

import com.typesafe.config.Config
import net.corda.data.config.Configuration
import net.corda.libs.configuration.write.ConfigWriteService
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kafka implementation of the [ConfigWriteService]
 * @property topicName the topic configurations will be published to
 */
class ConfigWriteServiceImpl (
    private val topicName: String,
    private val publisher: Publisher
) : ConfigWriteService {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Add the properties recorded in [config] to the component configuration.
     *
     * @param configKey containing identity, package version and component version
     * @param config typesafe config object
     */
    override fun appendConfiguration(
        configKey: CordaConfigurationKey,
        config: Config
    ) {
        throw NotImplementedError("Not yet implemented")
    }

    /**
     * Update the component configuration so that it matches [config].
     *
     * @param configKey containing identity, package version and component version
     * @param config typesafe config object
     */
    override fun updateConfiguration(
        configKey: CordaConfigurationKey,
        config: Config
    ) {
        val records = mutableListOf<Record<String, Configuration>>()
        for (key1 in config.root().keys) {
            val key1Config = config.getConfig(key1)
            for (key2 in key1Config.root().keys) {
                val content = Configuration(key1Config.atKey(key2).toString(), configKey.componentVersion.version)
                val record = Record(topicName, "$key1.$key2", content)
                log.debug {"Producing record: $key1.$key2\t$content"}
                records.add(record)
            }
        }

        publisher.publish(records)
    }
}
