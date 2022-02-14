package net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.schema.configuration.MessagingKeys.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingKeys.Bus.KAFKA_PROPERTIES
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import java.util.Properties

internal class ConfigResolverImpl(private val smartConfigFactory: SmartConfigFactory) : ConfigResolver {

    private companion object {
        private val logger = contextLogger()

        private const val ENFORCED_CONFIG_FILE = "messaging-enforced.conf"
        private const val DEFAULT_CONFIG_FILE = "messaging-defaults.conf"

        private const val EXPECTED_BUS_TYPE = "KAFKA"
    }

    private val defaults = getResourceConfig(DEFAULT_CONFIG_FILE)
    private val enforced = getResourceConfig(ENFORCED_CONFIG_FILE)

    override fun resolve(busConfig: SmartConfig, role: String, configParams: SmartConfig): Properties {
        // TODO fix paths here. Does the returned config when you do getConfig have the path stripped?
        if (busConfig.getString(BUS_TYPE) != EXPECTED_BUS_TYPE) {
            throw CordaMessageAPIConfigException("foo")
        }

        val kafkaParams = busConfig.getConfig(KAFKA_PROPERTIES)
        val resolvedConfig = enforced
            .withFallback(kafkaParams)
            .withFallback(configParams)
            .withFallback(defaults)
            .resolve()

        logger.info("Resolved kafka configuration: ${resolvedConfig.root().render()}")

        // Trim down to just the Kafka config for the specified role.
        val roleConfig = resolvedConfig.getConfig(role)
        val properties = roleConfig.toKafkaProperties()
        logger.info("Kafka properties for role $role: $properties")
        return properties
    }

    /**
     * Retrieve a resource from this bundle and convert it to a SmartConfig object.
     *
     * If this is running outside OSGi (e.g. a unit test) then fall back to standard Java classloader mechanisms.
     */
    private fun getResourceConfig(resource: String): SmartConfig {
        val bundle: Bundle? = FrameworkUtil.getBundle(this::class.java)
        val url = bundle?.getResource(resource)
            ?: this::class.java.classLoader.getResource(resource) ?: throw CordaMessageAPIConfigException("foo") // TODO
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
}