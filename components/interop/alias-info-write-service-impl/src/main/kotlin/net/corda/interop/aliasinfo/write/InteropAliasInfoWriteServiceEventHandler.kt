package net.corda.interop.aliasinfo.write

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory


/**
 * Handler for interop alias info write service lifecycle events.
 */
class InteropAliasInfoWriteServiceEventHandler(
    private val configurationReadService: ConfigurationReadService
) : LifecycleEventHandler {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is StopEvent -> onStopEvent()
            is ConfigChangedEvent -> onConfigChangeEvent(event, coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration = coordinator.followStatusChangesByName(setOf(
            LifecycleCoordinatorName.forComponent<InteropAliasInfoWriteService>()
        ))
    }

    private fun onStopEvent() {
        configSubscription?.close()
        configSubscription = null
        registration?.close()
        registration = null
    }

    @Suppress("UNUSED_VARIABLE")
    private fun onConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return

        log.debug("Alias info write service (re)subscribing")

        // TODO re-register with saurabhs kafka topic

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configurationReadService.registerComponentForUpdates(coordinator, setOf(
                ConfigKeys.MESSAGING_CONFIG
            ))
        } else {
            configSubscription?.close()
        }
    }
}
