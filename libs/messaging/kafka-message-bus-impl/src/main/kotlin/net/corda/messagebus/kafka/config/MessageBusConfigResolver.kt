package net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PROPERTIES
import net.corda.v5.base.util.debug
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Resolve a Kafka bus configuration against the enforced and default configurations provided by the library.
 */
internal class MessageBusConfigResolver(private val smartConfigFactory: SmartConfigFactory) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val ENFORCED_CONFIG_FILE = "kafka-messaging-enforced.conf"
        private const val DEFAULT_CONFIG_FILE = "kafka-messaging-defaults.conf"

        private const val EXPECTED_BUS_TYPE = "KAFKA"

        private const val GROUP_PATH = "group"
        private const val CLIENT_ID_PATH = "clientId"
        private const val TRANSACTIONAL_ID_PATH = "transactionalId"
    }

    private val defaults = getResourceConfig(DEFAULT_CONFIG_FILE)
    private val enforced = getResourceConfig(ENFORCED_CONFIG_FILE)

    /**
     * Resolve the provided configuration and return a valid set of Kafka properties suitable for the given role.
     *
     * @param messageBusConfig The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library.
     * @param rolePath The role to be configured. This is a path representing the object type being created at the patterns
     *             layer and a description of which consumer or producer is requested.
     * @param configParams A config object containing parameters to resolve against. Should be obtained from the
     *                     required configuration provided to the builders.
     * @return Kafka properties to be used for the given role type based on the bus config and config params
     */
    private fun resolve(messageBusConfig: SmartConfig, rolePath: String, configParams: SmartConfig): Properties {
        val busType = messageBusConfig.getString(BUS_TYPE)
        if (busType != EXPECTED_BUS_TYPE) {
            throw CordaMessageAPIConfigException(
                "Tried to configure the Kafka bus but received $busType configuration instead"
            )
        }

        val kafkaParams = messageBusConfig.getConfig(KAFKA_PROPERTIES)
        val resolvedConfig = enforced
            .withFallback(kafkaParams)
            .withFallback(configParams)
            .withFallback(defaults)
            .resolve()

        logger.debug {"Resolved kafka configuration: ${resolvedConfig.toSafeConfig().root().render()}" }

        // Trim down to just the Kafka config for the specified role.
        val roleConfig = resolvedConfig.getConfig("roles.$rolePath")
        val properties = roleConfig.toKafkaProperties()
        logger.debug {"Kafka properties for role $rolePath: $properties" }
        return properties
    }

    /**
     * Resolve the provided configuration and return a valid set of Kafka properties suitable for the given role
     * as well as a concrete class containing user configurable consumer values.
     *
     * @param messageBusConfig The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library.
     * @param consumerConfig User configurable values as well as the role to extract config for from the [messageBusConfig]
     * @return Resolved user configurable consumer values and kafka properties to be used for the given role type
     */
    fun resolve(messageBusConfig: SmartConfig, consumerConfig: ConsumerConfig): Pair<ResolvedConsumerConfig, Properties> {
        val kafkaProperties = resolve(messageBusConfig, consumerConfig.role.configPath, consumerConfig.toSmartConfig())
        val resolvedConfig = ResolvedConsumerConfig(
            consumerConfig.group,
            consumerConfig.clientId,
            messageBusConfig.getString(BootConfig.TOPIC_PREFIX)
        )
        return Pair(resolvedConfig, kafkaProperties)
    }

    /**
     * Resolve the provided configuration and return a valid set of Kafka properties suitable for the given role
     * as well as a concrete class containing user configurable producer values.
     *
     * @param messageBusConfig The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library.
     * @param producerConfig User configurable values as well as the role to extract config for from the [messageBusConfig]
     * @return Resolved user configurable Kafka values and kafka properties to be used for the given role type
     */
    fun resolve(messageBusConfig: SmartConfig, producerConfig: ProducerConfig): Pair<ResolvedProducerConfig, Properties> {
        val kafkaProperties = resolve(messageBusConfig, producerConfig.role.configPath, producerConfig.toSmartConfig())
        val resolvedConfig = ResolvedProducerConfig(
            producerConfig.clientId,
            producerConfig.transactional,
            messageBusConfig.getString(BootConfig.TOPIC_PREFIX)
        )
        return Pair(resolvedConfig, kafkaProperties)
    }

    /**
     * Retrieve a resource from this bundle and convert it to a SmartConfig object.
     *
     * If this is running outside OSGi (e.g. a unit test) then fall back to standard Java classloader mechanisms.
     */
    private fun getResourceConfig(resource: String): SmartConfig {
        val bundle = FrameworkUtil.getBundle(this::class.java)
        val url = bundle?.getResource(resource)
            ?: this::class.java.classLoader.getResource(resource)
            ?: throw CordaMessageAPIConfigException(
                "Failed to get resource $resource from Kafka bus implementation bundle"
            )
        val config = ConfigFactory.parseURL(url)
        return smartConfigFactory.create(config)
    }

    private fun SmartConfig.toKafkaProperties(): Properties {
        val properties = Properties()
        for ((key, _) in this.entrySet()) {
            properties.setProperty(key, this.getString(key))
        }
        return properties
    }

    // All parameters in the enforced and default config files must be specified. These functions insert dummy values
    // for those parameters that don't matter when resolving the config.
    private fun ConsumerConfig.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    GROUP_PATH to group,
                    CLIENT_ID_PATH to clientId,
                    TRANSACTIONAL_ID_PATH to "<undefined>"
                )
            )
        )
    }

    private fun ProducerConfig.toSmartConfig(): SmartConfig {
        val transactionalId = if (transactional) {
            "$clientId-$instanceId"
        } else {
            null
        }
        return smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    CLIENT_ID_PATH to clientId,
                    GROUP_PATH to "<undefined>",
                    TRANSACTIONAL_ID_PATH to transactionalId
                )
            )
        )
    }
}
