package net.corda.virtualnode.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.common.ConfigChangedEvent
import net.corda.virtualnode.common.MessagingConfigEventHandler
import net.corda.virtualnode.impl.VirtualNodeInfoProcessor
import org.slf4j.Logger

/**
 * Handle the events inside the [VirtualNodeInfo] service component.
 *
 * Message processing is in the processor, and the lifecycle is in the component.
 */
class VirtualNodeInfoReaderEventHandler(
    configurationReadService: ConfigurationReadService,
    private val virtualNodeInfoProcessor: VirtualNodeInfoProcessor,
    private val subscriptionFactory: SubscriptionFactory,
    private val instanceId: Int?,
    configChangedEventCallback: (ConfigChangedEvent) -> Unit
) : LifecycleEventHandler, AutoCloseable {
    companion object {
        internal const val GROUP_NAME = "VIRTUAL_NODE_INFO_READER"
        val log: Logger = contextLogger()
    }

    /**
     * Defer to a common event handler that returns us the correct config when the configuration
     * service has started.
     */
    private val messagingConfigEventHandler =
        MessagingConfigEventHandler(configurationReadService, configChangedEventCallback, this::onConfig)

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
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) =
        messagingConfigEventHandler.processEvent(event, coordinator)

    /** Resubscribe with the given config */
    private fun onConfig(coordinator: LifecycleCoordinator, config: SmartConfig) {
        log.debug { "Virtual Node Info Service (re)subscribing" }
        coordinator.updateStatus(LifecycleStatus.DOWN)
        virtualNodeInfoProcessor.clear()
        subscription?.close()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, VIRTUAL_NODE_INFO_TOPIC, instanceId),
            virtualNodeInfoProcessor,
            config
        )
        subscription?.start()
    }

    override fun close() {
        messagingConfigEventHandler.close()
        subscription?.close()
    }
}
