package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationReadException
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

internal class ConfigReadServiceEventHandler(
    private val subscriptionFactory: SubscriptionFactory
) : LifecycleEventHandler {

    private var bootstrapConfig: SmartConfig? = null
    private var subscription: CompactedSubscription<String, Configuration>? = null
    private var subReg: RegistrationHandle? = null

    private val registrations = mutableSetOf<ConfigurationChangeRegistration>()
    private val configuration = mutableMapOf<String, SmartConfig>()

    private companion object {
        private val logger = contextLogger()

        private const val GROUP = "CONFIGURATION_READ"
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Configuration read service starting up." }
                if (bootstrapConfig != null) {
                    coordinator.postEvent(SetupSubscription())
                }
            }
            is BootstrapConfigProvided -> {
                handleBootstrapConfig(event.config, coordinator)
            }
            is SetupSubscription -> {
                setupSubscription(coordinator)
            }
            is NewConfigReceived -> {
                for ((key, value) in event.config) {
                    configuration[key] = value
                }
                registrations.forEach { it.invoke(event.config.keys, configuration) }
            }
            is ConfigRegistrationOpen -> {
                registrations.add(event.registration)
                if (configuration.keys.isNotEmpty()) {
                    event.registration.invoke(configuration.keys, configuration)
                }
            }
            is ConfigRegistrationClose -> {
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
                subscription?.close()
                subscription = null
            }
            is ErrorEvent -> {
                logger.error(
                    "An error occurred in the configuration read service: ${event.cause.message}.",
                    event.cause
                )
            }
        }
    }

    private fun setupSubscription(coordinator: LifecycleCoordinator) {
        val config = bootstrapConfig
            ?: throw ConfigurationReadException(
                "Cannot setup the subscription to config topic with no bootstrap configuration"
            )
        if (subscription != null) {
            throw ConfigurationReadException("Subscription to config topic already exists when setup requested")
        }
        // The configuration passed through here might not be quite correct - boot configuration needs to be properly
        // defined. May also be relevant for secret service configuration in the processor.
        val sub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP, CONFIG_TOPIC),
            ConfigProcessor(coordinator),
            config
        )
        subReg = coordinator.followStatusChangesByName(setOf(sub.subscriptionName))
        subscription = sub
        sub.start()
    }

    private fun handleBootstrapConfig(config: SmartConfig, coordinator: LifecycleCoordinator) {
        if (bootstrapConfig == null) {
            logger.debug { "Bootstrap config received: $config" }
            bootstrapConfig = config
            configuration[BOOT_CONFIG] = config
            coordinator.postEvent(SetupSubscription())
        } else if (bootstrapConfig != config) {
            val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                    "different config. Current: $bootstrapConfig, New: $config"
            logger.error(errorString)
            throw ConfigurationReadException(errorString)
        } else {
            logger.debug { "Duplicate bootstrap configuration received." }
        }
    }
}
