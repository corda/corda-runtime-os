package net.corda.virtualnode.common

import net.corda.configuration.read.ConfigKeys
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/**
 * Event handler that specifically handles, and calls back on changes to the MESSAGING config.
 *
 * You want to use this if you want your reader/writers
 * correctly constructed with the currently correct Kafka hostname and port names (and if it changes dynamically).
 *
 * This class handles the events, but also uses a callback (see params) to publish
 * a [ConfigChangedEvent] via the correct coordinator.
 *
 * @param configChangedEventCallback called when a [ConfigChangedEvent] is generated,
 * You most likely want to simply pass this on to the coordinator to post, i.e.
 *
 *
 *         private fun onConfigChangeEvent(event: ConfigChangedEvent) = coordinator.postEvent(event)
 *
 *
 * @param configCallback called when we finally have a [SmartConfig] with _messaging_ configuration
 * that can be used.
 *
 * You will likely want to call some custom code elsewhere *and* use the coordinator to flag
 * that the component is up, i.e.
 *
 *
 *     private fun onConfig(coordinator: LifecycleCoordinator, config: SmartConfig) {
 *          coordinator.updateStatus(LifecycleStatus.DOWN)
 *          yourCodeUsingValid(config)
 *          coordinator.updateStatus(LifecycleStatus.UP)
 *     }
 *
 */
class MessagingConfigEventHandler(
    private val configurationReadService: ConfigurationReadService,
    private val configChangedEventCallback: (ConfigChangedEvent) -> Unit,
    private val configCallback: (LifecycleCoordinator, SmartConfig) -> Unit
) : ConfigurationHandler, LifecycleEventHandler, AutoCloseable {
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    /**
     * We received the following flow of events before the component is fully configured and
     * ready to publish:
     *
     *      onStart ->
     *      onRegistrationStatusChangeEvent ->
     *      onNewConfiguration ->
     *      onConfigChangedEvent ->
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEventReceived(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStopEvent() {
        registration?.close()
        registration = null
    }

    /**
     * We only receive this event if the config contains the information we explicitly
     * require as defined in [onNewConfiguration]
     */
    private fun onConfigChangedEventReceived(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        configCallback(coordinator, event.config)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configurationReadService.registerForUpdates(this)
        } else {
            configSubscription?.close()
        }
    }

    /** Only raise a [ConfigChangedEvent] is the Kafka messaging config has been sent */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (ConfigKeys.MESSAGING_KEY in changedKeys) {
            configChangedEventCallback(ConfigChangedEvent(config[ConfigKeys.MESSAGING_KEY]!!))
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    override fun close() {
        configSubscription?.close()
        registration?.close()
    }
}
