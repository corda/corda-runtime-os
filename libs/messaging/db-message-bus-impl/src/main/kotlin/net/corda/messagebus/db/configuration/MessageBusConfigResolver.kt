package net.corda.messagebus.db.configuration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.configuration.getStringOrDefault
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.schema.configuration.MessagingConfig.Bus.AUTO_OFFSET_RESET
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.DB_MAX_POLL_RECORDS
import net.corda.schema.configuration.MessagingConfig.Bus.DB_PROPERTIES
import net.corda.schema.configuration.MessagingConfig.Bus.JDBC_PASS
import net.corda.schema.configuration.MessagingConfig.Bus.JDBC_URL
import net.corda.schema.configuration.MessagingConfig.Bus.JDBC_USER
import net.corda.v5.base.util.debug
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory

/**
 * Resolve a DB bus configuration against the enforced and default configurations provided by the library.
 */
internal class MessageBusConfigResolver(private val smartConfigFactory: SmartConfigFactory) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val ENFORCED_CONFIG_FILE = "messaging-enforced.conf"
        private const val DEFAULT_CONFIG_FILE = "messaging-defaults.conf"

        private const val DATABASE_BUS_TYPE = "DATABASE"
        private const val INMEMORY_BUS_TYPE = "INMEMORY"

        private val EXPECTED_BUS_TYPES = listOf(DATABASE_BUS_TYPE, INMEMORY_BUS_TYPE)
        private const val GROUP_PATH = "group"
        private const val CLIENT_ID_PATH = "clientId"
        private const val INSTANCE_ID_PATH = "instanceId"

    }

    private val defaults = getResourceConfig(DEFAULT_CONFIG_FILE)
    private val enforced = getResourceConfig(ENFORCED_CONFIG_FILE)

    /**
     * Resolve the provided configuration and return a valid set of DB properties suitable for the given role.
     *
     * @param messageBusConfig The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library.
     * @param rolePath The role to be configured. This is a path representing the object type being created at the patterns
     *             layer and a description of which consumer or producer is requested.
     * @param configParams A config object containing parameters to resolve against. Should be obtained from the
     *                     required configuration provided to the builders.
     * @return DB properties to be used for the given role type based on the bus config and config params
     */
    private fun resolve(messageBusConfig: SmartConfig, rolePath: String, configParams: SmartConfig): Pair<SmartConfig, DbAccessProperties> {
        val busType = messageBusConfig.getString(BUS_TYPE)
        if (busType !in EXPECTED_BUS_TYPES) {
            throw CordaMessageAPIConfigException(
                "Tried to configure the DB/In-Mem bus but received $busType configuration instead"
            )
        }

        val dbParams = if (messageBusConfig.hasPath(DB_PROPERTIES)) {
            messageBusConfig.getConfig(DB_PROPERTIES)
        } else {
            SmartConfigImpl.empty()
        }
        val resolvedConfig = enforced
            .withFallback(dbParams)
            .withFallback(configParams)
            .withFallback(defaults)
            .resolve()

        val jdbcProperties = if(busType == DATABASE_BUS_TYPE) {
            DbAccessProperties(
                dbParams.getStringOrNull(JDBC_URL),
                dbParams.getStringOrDefault(JDBC_USER, ""),
                dbParams.getStringOrDefault(JDBC_PASS, "")
            )
        } else {
            DbAccessProperties(
                null,
                "",
                ""
            )
        }

        logger.debug { "Resolved DB configuration: ${resolvedConfig.toSafeConfig().root().render()}" }

        // Trim down to just the DB config for the specified role.
        return resolvedConfig.getConfig("roles.$rolePath") to jdbcProperties
    }

    /**
     * Resolve the provided configuration and return a valid set of DB properties suitable for the given role
     * as well as a concrete class containing user configurable consumer values.
     *
     * @param messageBusConfig The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library.
     * @param consumerConfig User configurable values as well as the role to extract config for from the [messageBusConfig]
     * @return Resolved user configurable consumer values to be used for the given role type
     */
    fun resolve(messageBusConfig: SmartConfig, consumerConfig: ConsumerConfig): ResolvedConsumerConfig {
        val (dbProperties, jdbcProperties) = resolve(messageBusConfig, consumerConfig.role.configPath, consumerConfig.toSmartConfig())
        return ResolvedConsumerConfig(
            consumerConfig.group,
            consumerConfig.clientId,
            dbProperties.getInt(DB_MAX_POLL_RECORDS),
            CordaOffsetResetStrategy.valueOf(dbProperties.getString(AUTO_OFFSET_RESET).uppercase()),
            jdbcProperties.jdbcUrl,
            jdbcProperties.username,
            jdbcProperties.password
        )
    }

    /**
     * Resolve the provided configuration and return a valid set of DB properties suitable for the given role
     * as well as a concrete class containing user configurable producer values.
     *
     * @param messageBusConfig The supplied message bus configuration. Must match the schema used in the defaults and enforced
     *               config files included with this library.
     * @param producerConfig User configurable values as well as the role to extract config for from the [messageBusConfig]
     * @return Resolved user configurable DB values to be used for the given role type
     */
    fun resolve(messageBusConfig: SmartConfig, producerConfig: ProducerConfig): ResolvedProducerConfig {
        val (_, jdbcProperties) = resolve(messageBusConfig, producerConfig.role.configPath, producerConfig.toSmartConfig())
        return ResolvedProducerConfig(
            producerConfig.clientId,
            jdbcProperties.jdbcUrl,
            jdbcProperties.username,
            jdbcProperties.password
        )
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
                "Failed to get resource $resource from DB bus implementation bundle"
            )
        val config = ConfigFactory.parseURL(url)
        return smartConfigFactory.create(config)
    }

    // All parameters in the enforced and default config files must be specified. These functions insert dummy values
    // for those parameters that don't matter when resolving the config.
    private fun ConsumerConfig.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    GROUP_PATH to group,
                    CLIENT_ID_PATH to clientId,
                    INSTANCE_ID_PATH to "<undefined>"
                )
            )
        )
    }

    private fun ProducerConfig.toSmartConfig(): SmartConfig {
        return smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    CLIENT_ID_PATH to clientId,
                    INSTANCE_ID_PATH to instanceId,
                    GROUP_PATH to "<undefined>"
                )
            )
        )
    }

    data class DbAccessProperties(
        val jdbcUrl: String?,
        val username: String,
        val password: String,
    )
}
