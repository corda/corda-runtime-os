package net.corda.membership.impl.read.lifecycle

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
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.subscription.MemberListProcessor
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.slf4j.LoggerFactory

/**
 * Lifecycle handler for the membership group read component.
 */
interface MembershipGroupReadLifecycleHandler : LifecycleEventHandler {
    private companion object {
        const val SUBSCRIPTION_RESOURCE = "MemberOpsService.SUBSCRIPTION_RESOURCE"
        const val CONSUMER_GROUP = "MEMBERSHIP_GROUP_READER"
    }

    /**
     * Default implementation.
     */
    class Impl(
        private val configurationReadService: ConfigurationReadService,
        private val subscriptionFactory: SubscriptionFactory,
        private val memberInfoFactory: MemberInfoFactory,
        private val activateImplFunction: (String, MembershipGroupReadCache) -> Unit,
        private val deactivateImplFunction: (String) -> Unit
    ) : MembershipGroupReadLifecycleHandler {
        companion object {
            val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        private var configRegistrationHandle: AutoCloseable? = null
        private var dependencyRegistrationHandle: RegistrationHandle? = null

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is StartEvent -> {
                    dependencyRegistrationHandle?.close()
                    dependencyRegistrationHandle = coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
                is StopEvent -> {
                    deactivateImplFunction.invoke("Stopped component due to StopEvent received.")
                    dependencyRegistrationHandle?.close()
                    configRegistrationHandle?.close()
                }
                is RegistrationStatusChangeEvent -> handleRegistrationStatusChangeEvent(event, coordinator)
                is ConfigChangedEvent -> handleConfigChangedEvent(event, coordinator)
            }
        }

        private fun handleRegistrationStatusChangeEvent(
            event: RegistrationStatusChangeEvent,
            coordinator: LifecycleCoordinator,
        ) {
            logger.info(MembershipGroupReaderProvider::class.simpleName + " handling registration changed event.")
            // Respond to config read service lifecycle status change
            when (event.status) {
                LifecycleStatus.UP -> {
                    configRegistrationHandle?.close()
                    configRegistrationHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                    )
                }
                else -> {
                    deactivateImplFunction.invoke("Component is inactive due to down dependency.")
                    configRegistrationHandle?.close()
                }
            }
        }

        private fun onReady(membershipGroupReadCache: MembershipGroupReadCache) {
            activateImplFunction.invoke(
                "Starting component due to dependencies UP and configuration received.",
                membershipGroupReadCache,
            )
        }

        private fun handleConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
            logger.info(MembershipGroupReaderProvider::class.simpleName + " handling new config event.")
            val membershipGroupReadCache = MembershipGroupReadCache.Impl()
            coordinator.createManagedResource(SUBSCRIPTION_RESOURCE) {
                subscriptionFactory.createCompactedSubscription(
                    subscriptionConfig = SubscriptionConfig(
                        CONSUMER_GROUP,
                        Schemas.Membership.MEMBER_LIST_TOPIC
                    ),
                    processor = MemberListProcessor(membershipGroupReadCache, memberInfoFactory) { cache -> onReady(cache) },
                    messagingConfig = event.config.getConfig(MESSAGING_CONFIG),
                ).also { it.start() }
            }
        }
    }
}
