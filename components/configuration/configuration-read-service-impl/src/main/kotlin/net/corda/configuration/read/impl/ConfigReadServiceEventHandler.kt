package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationReadException
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

internal class ConfigReadServiceEventHandler(
    private val readServiceFactory: ConfigReaderFactory,
    private val callbackHandles: ConfigurationHandlerStorage
) : LifecycleEventHandler {

    private var serviceUpCallback: AutoCloseable? = null
    private var bootstrapConfig: SmartConfig? = null
    private var subscription: ConfigReader? = null

    private companion object {
        private val logger = contextLogger()
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
                if (bootstrapConfig == null) {
                    logger.debug { "Bootstrap config received: ${event.config}" }
                    bootstrapConfig = event.config
                    coordinator.postEvent(SetupSubscription())
                } else if (bootstrapConfig != event.config) {
                    val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                            "different config. Current: $bootstrapConfig, New: ${event.config}"
                    logger.error(errorString)
                    throw ConfigurationReadException(errorString)
                } else {
                    logger.debug { "Duplicate bootstrap configuration received." }
                }
            }
            is SetupSubscription -> {
                setupSubscription(coordinator)
            }
            is StopEvent -> {
                logger.debug { "Configuration read service stopping." }
                callbackHandles.removeSubscription()
                subscription?.stop()
                subscription = null
                serviceUpCallback?.close()
                serviceUpCallback = null
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
            ?: throw IllegalArgumentException("Cannot setup the subscription with no bootstrap configuration")
        if (subscription != null) {
            throw IllegalArgumentException("The subscription already exists")
        }
        val sub = readServiceFactory.createReader(config)
        subscription = sub
        callbackHandles.addSubscription(sub)
        serviceUpCallback = sub.registerCallback(ServiceUpHandler(coordinator))
        sub.start()
    }

    internal class ServiceUpHandler(
        private val coordinator: LifecycleCoordinator
    ) : ConfigListener {
        override fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>) {
            if (coordinator.status == LifecycleStatus.DOWN) {
                coordinator.updateStatus(LifecycleStatus.UP, "Connected to configuration repository.")
            }
        }
    }

}
