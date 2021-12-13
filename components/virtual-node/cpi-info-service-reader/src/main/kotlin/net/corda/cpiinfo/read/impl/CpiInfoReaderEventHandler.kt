package net.corda.cpiinfo.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.CpiInfoReader
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.packaging.CPIMetadata
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.common.ConfigChangedEvent
import net.corda.virtualnode.common.MessagingConfigEventHandler
import org.slf4j.Logger

/**
 * Handle the events inside the [CpiInfoReader] component.
 *
 * Message processing is in the processor, and the lifecycle is in the component.
 */
class CpiInfoReaderEventHandler(
    configurationReadService: ConfigurationReadService,
    private val cpiInfoProcessor: CpiInfoReaderProcessor,
    private val subscriptionFactory: SubscriptionFactory,
    private val instanceId: Int?,
    configChangedEventCallback: (ConfigChangedEvent) -> Unit
) : LifecycleEventHandler, AutoCloseable {
    companion object {
        internal const val GROUP_NAME = "CPI_INFO_READER"
        val log: Logger = contextLogger()
    }

    /**
     * Defer to a common event handler that returns us the correct config when the configuration
     * service has started.
     */
    private val messagingConfigEventHandler =
        MessagingConfigEventHandler(configurationReadService, configChangedEventCallback, this::onConfig)

    private var subscription: CompactedSubscription<CPIIdentifier, CPIMetadata>? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) =
        messagingConfigEventHandler.processEvent(event, coordinator)

    /** Resubscribe with the given config */
    private fun onConfig(coordinator: LifecycleCoordinator, config: SmartConfig) {
        log.debug { "Cpi Info Read Service (re)subscribing" }

        coordinator.updateStatus(LifecycleStatus.DOWN)

        cpiInfoProcessor.clear()
        subscription?.close()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, Schemas.CPI_INFO_TOPIC, instanceId),
            cpiInfoProcessor,
            config
        )
        subscription?.start()
    }

    override fun close() {
        messagingConfigEventHandler.close()
        subscription?.close()
    }
}
