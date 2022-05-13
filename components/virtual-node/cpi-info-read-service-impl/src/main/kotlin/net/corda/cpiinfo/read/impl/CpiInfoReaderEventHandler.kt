package net.corda.cpiinfo.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.packaging.CpiIdentifier
import net.corda.data.packaging.CpiMetadata
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.common.ConfigChangedEvent
import net.corda.virtualnode.common.MessagingConfigEventHandler

/**
 * Handle the events inside the [CpiInfoReader] component.
 *
 * Message processing is in the processor, and the lifecycle is in the component.
 */
class CpiInfoReaderEventHandler(
    configurationReadService: ConfigurationReadService,
    private val cpiInfoProcessor: CpiInfoReaderProcessor,
    private val subscriptionFactory: SubscriptionFactory,
    configChangedEventCallback: (ConfigChangedEvent) -> Unit
) : LifecycleEventHandler, AutoCloseable {
    companion object {
        internal const val GROUP_NAME = "CPI_INFO_READER"
        private val log = contextLogger()
    }

    /**
     * Defer to a common event handler that returns us the correct config when the configuration
     * service has started.
     */
    private val messagingConfigEventHandler =
        MessagingConfigEventHandler(configurationReadService, configChangedEventCallback, this::onConfig)

    private var subscription: CompactedSubscription<CpiIdentifier, CpiMetadata>? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) =
        messagingConfigEventHandler.processEvent(event, coordinator)

    /** Resubscribe with the given config */
    private fun onConfig(coordinator: LifecycleCoordinator, config: SmartConfig) {
        log.debug { "Cpi Info Read Service (re)subscribing" }

        cpiInfoProcessor.clear()
        subscription?.close()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, CPI_INFO_TOPIC),
            cpiInfoProcessor,
            config
        )
        subscription?.start()

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    override fun close() {
        messagingConfigEventHandler.close()
        subscription?.close()
    }
}
