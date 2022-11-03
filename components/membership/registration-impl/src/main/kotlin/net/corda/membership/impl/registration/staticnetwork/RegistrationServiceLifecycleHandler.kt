package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.data.KeyValuePairList
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
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.staticnetwork.cache.GroupParametersCache
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_STATIC_NETWORK_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG

class RegistrationServiceLifecycleHandler(
    staticMemberRegistrationService: StaticMemberRegistrationService
) : LifecycleEventHandler {
    companion object {
        const val CONSUMER_GROUP = "STATIC_GROUP_DEFINITION"
    }

    // for watching the config changes
    private var configHandle: AutoCloseable? = null
    // for checking the components' health
    private var componentHandle: AutoCloseable? = null
    private var subRegistrationHandle: RegistrationHandle? = null

    private val publisherFactory = staticMemberRegistrationService.publisherFactory

    private val subscriptionFactory = staticMemberRegistrationService.subscriptionFactory

    private val configurationReadService = staticMemberRegistrationService.configurationReadService

    private val platformInfoProvider = staticMemberRegistrationService.platformInfoProvider

    private val keyEncodingService = staticMemberRegistrationService.keyEncodingService

    private var _groupParametersCache: GroupParametersCache? = null

    private var _publisher: Publisher? = null

    private var subscription: Subscription<String, KeyValuePairList>? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    val groupParametersCache: GroupParametersCache
        get() = _groupParametersCache ?: throw IllegalArgumentException("GroupParametersCache is not initialized.")

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when(event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<HSMRegistrationClient>()
            )
        )
    }

    private fun handleStopEvent() {
        componentHandle?.close()
        configHandle?.close()
        _publisher?.close()
        _publisher = null
        subRegistrationHandle?.close()
        subRegistrationHandle = null
        subscription?.close()
        subscription = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                if (event.registration == subRegistrationHandle) {
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                }
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                configHandle?.close()
                subRegistrationHandle?.close()
                subRegistrationHandle = null
                subscription?.close()
                subscription = null
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("static-member-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        _groupParametersCache = GroupParametersCache(platformInfoProvider, publisher, keyEncodingService)

        subscription?.close()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CONSUMER_GROUP, MEMBERSHIP_STATIC_NETWORK_TOPIC),
            Processor(groupParametersCache),
            event.config.getConfig(MESSAGING_CONFIG)
        ).also {
            it.start()
            subRegistrationHandle = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
        }

        if(coordinator.status != LifecycleStatus.UP) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    internal inner class Processor(
        private val groupParametersCache: GroupParametersCache
    ) : CompactedProcessor<String, KeyValuePairList> {
        override val keyClass = String::class.java
        override val valueClass = KeyValuePairList::class.java
        override fun onNext(
            newRecord: Record<String, KeyValuePairList>,
            oldValue: KeyValuePairList?,
            currentData: Map<String, KeyValuePairList>
        ) {
            with(newRecord) {
                value?.let { groupParametersCache.set(key, it) }
            }
        }

        override fun onSnapshot(currentData: Map<String, KeyValuePairList>) {
            currentData.entries.forEach {
                groupParametersCache.set(it.key, it.value)
            }
        }
    }
}