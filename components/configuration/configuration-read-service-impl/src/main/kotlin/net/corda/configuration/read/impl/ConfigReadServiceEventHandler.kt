package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationReadException
import net.corda.data.Fingerprint
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.AvroSchemas.Companion.AVRO_SCHEMA_TOPIC
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory

internal class ConfigReadServiceEventHandler(
    private val subscriptionFactory: SubscriptionFactory,
    private val configMerger: ConfigMerger,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val publisherFactory: PublisherFactory
) : LifecycleEventHandler {

    internal var configProcessor: ConfigProcessor? = null
    internal var avroSchemaProcessor: AvroSchemaProcessor? = null

    private var bootstrapConfig: SmartConfig? = null
    private var configSubscription: CompactedSubscription<String, Configuration>? = null
    private var avroSchemaSubscription: CompactedSubscription<Fingerprint, String>? = null
    private var subReg: RegistrationHandle? = null

    private val registrations = mutableSetOf<ConfigurationChangeRegistration>()
    private val configuration = mutableMapOf<String, SmartConfig>()

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val CONFIG_GROUP = "CONFIGURATION_READ"
        private const val AVRO_GROUP = "AVRO_READ"
    }

    val publisher by lazy {
        publisherFactory.createPublisher(
            PublisherConfig(
                "ConfigReadServiceEventHandler Avro Schema publisher", false
            ), configuration.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Configuration read service starting up." }
                if (bootstrapConfig != null) {
                    coordinator.postEvent(SetupAvroSchemaSubscription())
                }
            }

            is BootstrapConfigProvided -> {
                // This will trigger SetupSubscription to be sent on new bootstrap configuration.
                handleBootstrapConfig(event.config, coordinator)
            }

            is SetupAvroSchemaSubscription -> {
                setupAvroSchemaSubscription(coordinator)
            }

            is SetupConfigSubscription -> {
                setupConfigSubscription(coordinator)
            }

            is NewConfigReceived -> {
                for ((key, config) in event.config) {
                    configuration[key] = config
                }
                publishAvroSchemas()
                registrations.forEach { it.invoke(event.config.keys, configuration) }
            }

            is ConfigRegistrationAdd -> {
                registrations.add(event.registration)
                if (configuration.keys.isNotEmpty()) {
                    event.registration.invoke(configuration.keys, configuration)
                }
            }

            is ConfigRegistrationRemove -> {
                registrations.remove(event.registration)
            }

            is RegistrationStatusChangeEvent -> {
                // Only registration is on the subscription
                if (event.status == LifecycleStatus.UP) {
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }

            is StopEvent -> {
                logger.debug { "Configuration read service stopping." }
                subReg?.close()
                configSubscription?.close()
                configSubscription = null
            }

            is ErrorEvent -> {
                logger.warn(
                    "An error occurred in the configuration read service: ${event.cause.message}.", event.cause
                )
            }
        }
    }

    private fun setupAvroSchemaSubscription(coordinator: LifecycleCoordinator) {
        val config = bootstrapConfig ?: throw ConfigurationReadException(
            "Cannot setup the subscription to avro schema topic with no bootstrap configuration"
        )
        if (avroSchemaSubscription != null) {
            throw ConfigurationReadException("Subscription to $AVRO_SCHEMA_TOPIC already exists when setup requested")
        }

        val avroSchemaProcessor = AvroSchemaProcessor(coordinator, avroSchemaRegistry)
        val sub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(AVRO_GROUP, AVRO_SCHEMA_TOPIC),
            avroSchemaProcessor,
            configMerger.getMessagingConfig(config, null)
        )
        subReg = coordinator.followStatusChangesByName(setOf(sub.subscriptionName))
        this.avroSchemaProcessor = avroSchemaProcessor
        avroSchemaSubscription = sub
        sub.start()
    }

    /**
     * We cannot publish avro schemas with the bootstrap config, publishers require the complete messaging config.
     */
    private fun publishAvroSchemas() {
        if (!configuration.containsKey(ConfigKeys.MESSAGING_CONFIG)) {
            return;
        }

        avroSchemaProcessor?.publishNewSchemas(publisher) ?: run { logger.info("No avro schema processor.") }
    }

    private fun setupConfigSubscription(coordinator: LifecycleCoordinator) {
        val config = bootstrapConfig ?: throw ConfigurationReadException(
            "Cannot setup the subscription to config topic with no bootstrap configuration"
        )
        if (configSubscription != null) {
            throw ConfigurationReadException("Subscription to $CONFIG_TOPIC already exists when setup requested")
        }
        // The configuration passed through here might not be quite correct - boot configuration needs to be properly
        // defined. May also be relevant for secret service configuration in the processor.
        val configProcessor = ConfigProcessor(coordinator, config.factory, config, configMerger)
        val sub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONFIG_GROUP, CONFIG_TOPIC), configProcessor, configMerger.getMessagingConfig(config, null)
        )
        subReg = coordinator.followStatusChangesByName(setOf(sub.subscriptionName))
        this.configProcessor = configProcessor
        configSubscription = sub
        sub.start()
    }

    private fun handleBootstrapConfig(config: SmartConfig, coordinator: LifecycleCoordinator) {
        if (bootstrapConfig == null) {
            logger.debug { "Bootstrap config received: $config" }
            bootstrapConfig = config
            configuration[BOOT_CONFIG] = config
            // Now that the bootstrap configuration has been received the component should set up the subscription.
            // Note that this must happen in a separate event as across restart the lifecycle should skip straight to
            // set up subscription step.
            coordinator.postEvent(SetupAvroSchemaSubscription())
        } else if (bootstrapConfig != config) {
            val errorString =
                "An attempt was made to set the bootstrap configuration twice with " + "different config. Current: $bootstrapConfig, New: $config"
            logger.error(errorString)
            throw ConfigurationReadException(errorString)
        } else {
            logger.debug { "Duplicate bootstrap configuration received." }
        }
    }
}
