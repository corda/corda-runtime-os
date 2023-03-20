package net.corda.virtualnode.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
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
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Handle the events inside the [VirtualNodeInfo] service component.
 *
 * Message processing is in the processor, and the lifecycle is in the component.
 */
class VirtualNodeInfoReaderEventHandler(
    private val configurationReadService: ConfigurationReadService,
    private val virtualNodeInfoProcessor: VirtualNodeInfoProcessor,
    private val subscriptionFactory: SubscriptionFactory,
) : LifecycleEventHandler {
    companion object {
        internal const val GROUP_NAME = "VIRTUAL_NODE_INFO_READER"
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var subscription: CompactedSubscription<HoldingIdentity, VirtualNodeInfo>? = null
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    private fun onStopEvent() {
        registration?.close()
        registration = null
        configSubscription?.close()
        configSubscription = null
        subscription?.close()
        subscription = null
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return

        log.debug { "Virtual Node Info Service (re)subscribing" }

        virtualNodeInfoProcessor.clear()
        subscription?.close()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, VIRTUAL_NODE_INFO_TOPIC),
            virtualNodeInfoProcessor,
            config
        )
        subscription?.start()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configSubscription = configurationReadService.registerComponentForUpdates(coordinator, setOf(ConfigKeys.MESSAGING_CONFIG))
        } else {
            coordinator.updateStatus(event.status)
            configSubscription?.close()
        }
    }
}
