package net.corda.virtualnode.impl

import net.corda.configuration.read.ConfigKeys
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
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
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger

/**
 * Handle the events inside the [VirtualNodeInfo] service component.
 *
 * Message processing is in the processor, and the lifecycle is in the component.
 */
class VirtualNodeInfoEventHandler(
    private val configurationReadService: ConfigurationReadService,
    private val virtualNodeInfoProcessor: VirtualNodeInfoProcessor,
    private val subscriptionFactory: SubscriptionFactory,
    private val configChangedEventCallback: (ConfigChangedEvent) -> Unit
) : ConfigurationHandler, LifecycleEventHandler, AutoCloseable {
    companion object {
        internal const val GROUP_NAME = "VIRTUAL_NODE_INFO_READER"
        val log: Logger = contextLogger()
    }

    private var configSubscription: AutoCloseable? = null

    private var registration: RegistrationHandle? = null

    private var subscription: CompactedSubscription<HoldingIdentity, VirtualNodeInfo>? = null

    /**
     * We communicate by event passing wherever possible.
     *
     * When a config change we are interested in is received, we _post a message_
     * containing that config, which then flows through the system.
     *
     *      onStart ->
     *      onRegistrationStatusChangeEvent ->
     *      onNewConfiguration ->
     *      onConfigChangedEvent ->
     *
     *  and then we can create a subscription with the _correct_ configuration.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    /** Start the configuration service, and register to listen to its events. */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    /** Stop subscriptions (but not the virtual node processor - this is _stop_ not _close_).  */
    private fun onStopEvent() {
        configSubscription?.close()
        registration?.close()
        registration = null
    }

    /** Received a changed configuration that is relevant to this component (Kafka config changes). */
    private fun onConfigChangedEvent(
        coordinator: LifecycleCoordinator,
        event: ConfigChangedEvent
    ) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        virtualNodeInfoProcessor.clear()
        resubscribe(event)
    }

    /** Resubscribe with the given config */
    private fun resubscribe(event: ConfigChangedEvent) {
        log.debug { "Virtual Node Info Service (re)subscribing" }
        subscription?.close()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, Schemas.VIRTUAL_NODE_INFO_TOPIC),
            virtualNodeInfoProcessor,
            event.config
        )
        subscription?.start()
    }

    /** Subscribe to changes in the configuration once this component is 'up'. */
    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configurationReadService.registerForUpdates(this)
        } else {
            configSubscription?.close()
        }
    }

    /** Configuration callback, for a change in Kafka (messaging) configuration, post (via a callback) as an event. */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (ConfigKeys.MESSAGING_KEY in changedKeys) {
            configChangedEventCallback(ConfigChangedEvent(config[ConfigKeys.MESSAGING_KEY]!!))
        }
    }

    override fun close() {
        subscription?.close()
        configSubscription?.close()
        registration?.close()
    }
}
