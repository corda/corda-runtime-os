package net.corda.interop.group.policy.read.impl

import java.util.*
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap


/**
 * Handler for interop group policy read service lifecycle events.
 */
class InteropGroupPolicyReadServiceEventHandler(
    private val configurationReadService: ConfigurationReadService,
    private val subscriptionFactory: SubscriptionFactory
) : LifecycleEventHandler {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val GROUP_POLICY_SUBSCRIPTION = "group.policy.subscription"
        const val SUBSCRIPTION_GROUP_NAME = "interop.group.policy.subscription.group"
    }

    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null
    private val groupPolicies = ConcurrentHashMap<UUID, String>()

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

    @Suppress("UNUSED_VARIABLE")
    private fun onConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return

        // Use debug rather than info
        log.info("Processing config update")
        coordinator.createManagedResource(GROUP_POLICY_SUBSCRIPTION) {
            subscriptionFactory.createCompactedSubscription(
                subscriptionConfig = SubscriptionConfig(
                    groupName = SUBSCRIPTION_GROUP_NAME,
                    Schemas.Flow.INTEROP_GROUP_POLICY_TOPIC,
                ),
                processor = Processor(),
                messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
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

    private inner class Processor : CompactedProcessor<UUID, String> {
        override val keyClass = UUID::class.java
        override val valueClass = String::class.java

        override fun onNext(
            newRecord: Record<UUID, String>,
            oldValue: String?,
            currentData: Map<UUID, String>,
        ) {
            log.info("onNext currentData=${currentData.size} newRecord=${newRecord}")
            val key = newRecord.key
            val newEntry = newRecord.value
            if (newEntry == null) {
                if (oldValue != null) {
                    groupPolicies.remove(
                        UUID.fromString(oldValue)
                    )
                }
            } else {
                addEntry(key, newEntry)
            }
        }

        override fun onSnapshot(currentData: Map<UUID, String>) {
            log.info("onSnapshot=${currentData.size}")
            currentData.entries.forEach {
                addEntry(it.key, it.value)
            }
        }
    }

    private fun addEntry(key: UUID, newEntry: String) {
        groupPolicies[key] = newEntry
    }

    fun getGroupPolicy(key: UUID) : String? {
        return groupPolicies[key]
    }
}