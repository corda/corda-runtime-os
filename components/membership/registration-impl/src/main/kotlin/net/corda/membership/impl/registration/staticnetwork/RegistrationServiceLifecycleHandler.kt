package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.data.membership.staticgroup.StaticGroupDefinition
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
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

        // Keys for resources managed by this components lifecycle coordinator. Note that this class is reliant on a
        // coordinator created elsewhere. It is therefore important to ensure that these keys do not clash with any
        // resources created in any other place that uses the same coordinator.
        private const val SUBSCRIPTION_RESOURCE = "SUBSCRIPTION_RESOURCE"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val COMPONENT_HANDLE = "COMPONENT_HANDLE"
    }

    private val publisherFactory = staticMemberRegistrationService.publisherFactory

    private val subscriptionFactory = staticMemberRegistrationService.subscriptionFactory

    private val configurationReadService = staticMemberRegistrationService.configurationReadService

    private val platformInfoProvider = staticMemberRegistrationService.platformInfoProvider

    private val keyEncodingService = staticMemberRegistrationService.keyEncodingService

    private var _groupParametersCache: GroupParametersCache? = null

    private var _publisher: Publisher? = null

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
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        coordinator.createManagedResource(COMPONENT_HANDLE) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<HSMRegistrationClient>()
                )
            )
        }
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        _publisher?.close()
        _publisher = null
        coordinator.closeManagedResources(setOf(SUBSCRIPTION_RESOURCE, CONFIG_HANDLE, COMPONENT_HANDLE))
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                val subHandle = coordinator.getManagedResource<MembershipSubscriptionAndRegistration>(
                    SUBSCRIPTION_RESOURCE
                )?.registrationHandle
                if (event.registration == subHandle) {
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }
                }
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                coordinator.closeManagedResources(setOf(SUBSCRIPTION_RESOURCE, CONFIG_HANDLE))
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

        coordinator.createManagedResource(SUBSCRIPTION_RESOURCE) {
            val subscription = subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CONSUMER_GROUP, MEMBERSHIP_STATIC_NETWORK_TOPIC),
                Processor(groupParametersCache),
                event.config.getConfig(MESSAGING_CONFIG)
            )
            subscription.start()
            val handle = coordinator.followStatusChangesByName(setOf(subscription.subscriptionName))
            MembershipSubscriptionAndRegistration(subscription, handle)
        }

        if(coordinator.status != LifecycleStatus.UP) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    internal inner class Processor(
        private val groupParametersCache: GroupParametersCache
    ) : CompactedProcessor<String, StaticGroupDefinition> {
        override val keyClass = String::class.java
        override val valueClass = StaticGroupDefinition::class.java
        override fun onNext(
            newRecord: Record<String, StaticGroupDefinition>,
            oldValue: StaticGroupDefinition?,
            currentData: Map<String, StaticGroupDefinition>
        ) {
            with(newRecord) {
                value?.let { groupParametersCache.set(key, it.groupParameters) }
            }
        }

        override fun onSnapshot(currentData: Map<String, StaticGroupDefinition>) {
            currentData.entries.forEach {
                groupParametersCache.set(it.key, it.value.groupParameters)
            }
        }
    }

    /**
     * Pair up the subscription to the compacted topic and the registration handle to that subscription.
     *
     * This allows us to enforce the close order on these two resources, which prevents an accidental extra DOWN event
     * from propagating when we're recreating the subscription.
     */
    private class MembershipSubscriptionAndRegistration(
        val subscription: Subscription<String, StaticGroupDefinition>,
        val registrationHandle: RegistrationHandle
    ) : Resource {
        override fun close() {
            // The close order here is important - closing the subscription first can result in spurious lifecycle
            // events being posted.
            registrationHandle.close()
            subscription.close()
        }
    }
}