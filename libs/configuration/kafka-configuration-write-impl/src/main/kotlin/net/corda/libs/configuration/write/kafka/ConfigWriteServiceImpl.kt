package net.corda.libs.configuration.write.kafka

import com.typesafe.config.Config
import net.corda.data.config.Configuration
import net.corda.libs.configuration.write.ConfigWriteService
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kafka implementation of the [ConfigWriteService]
 * @property topicName the topic configurations will be published to
 */
@Component(immediate = true, service = [ConfigWriteService::class])
class ConfigWriteServiceImpl(
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
        val content = Configuration(config.toString(), configKey.componentVersion.version)
        val recordKey = "${configKey.packageVersion.name}.${configKey.componentVersion.name}"
        val record = Record(topicName, recordKey, content)
        log.debug { "Producing record: $recordKey\t$content" }
        publisher.publish(listOf(record))
    }
}
