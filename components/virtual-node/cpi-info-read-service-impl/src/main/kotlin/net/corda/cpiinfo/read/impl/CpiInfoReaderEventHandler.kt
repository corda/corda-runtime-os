package net.corda.cpiinfo.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.packaging.CpiIdentifier
import net.corda.data.packaging.CpiMetadata
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
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory

/**
 * Handle the events inside the [CpiInfoReader] component.
 *
 * Message processing is in the processor, and the lifecycle is in the component.
 */
class CpiInfoReaderEventHandler(
    private val configurationReadService: ConfigurationReadService,
    private val cpiInfoProcessor: CpiInfoReaderProcessor,
    private val subscriptionFactory: SubscriptionFactory
) : LifecycleEventHandler {
    companion object {
        internal const val GROUP_NAME = "CPI_INFO_READER"
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var subscription: CompactedSubscription<CpiIdentifier, CpiMetadata>? = null
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
        configSubscription?.close()
        configSubscription = null
        registration?.close()
        registration = null
        subscription?.close()
        subscription = null
    }

    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return

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

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription =
                configurationReadService.registerComponentForUpdates(coordinator, setOf(ConfigKeys.MESSAGING_CONFIG))
        } else {
            configSubscription?.close()
        }
    }
}
