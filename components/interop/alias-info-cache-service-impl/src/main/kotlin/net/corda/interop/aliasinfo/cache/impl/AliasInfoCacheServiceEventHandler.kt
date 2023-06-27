package net.corda.interop.aliasinfo.cache.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.aliasinfo.cache.AliasInfoCacheService
import net.corda.interop.aliasinfo.processor.InteropIdentityProcessor
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.INTEROP_ALIAS_IDENTITY_TOPIC
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory


class AliasInfoCacheServiceEventHandler(
    private val configurationReadService: ConfigurationReadService,
    private val subscriptionFactory: SubscriptionFactory,
    private val cacheService: AliasInfoCacheService
) : LifecycleEventHandler {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val GROUP_NAME = "interop_alias_identity"
    }

    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is StopEvent -> onStopEvent()
            is ConfigChangedEvent -> onConfigChangeEvent(event, coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            else -> {
                log.error("Unexpected event: '$event'")
            }
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration = coordinator.followStatusChangesByName(setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        ))
    }

    private fun onStopEvent() {
        configSubscription?.close()
        configSubscription = null
        registration?.close()
        registration = null
    }


    private fun onConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)

        coordinator.createManagedResource("InteropAliasIdentityProcessor.subscription") {
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(GROUP_NAME, INTEROP_ALIAS_IDENTITY_TOPIC),
                InteropIdentityProcessor(cacheService),
                messagingConfig
            ).also {
                it.start()
            }
        }

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
