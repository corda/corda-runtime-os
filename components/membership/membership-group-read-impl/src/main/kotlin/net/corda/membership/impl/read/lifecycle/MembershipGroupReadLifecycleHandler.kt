package net.corda.membership.impl.read.lifecycle

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.membership.impl.read.subscription.MembershipGroupReadSubscriptions
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger

/**
 * Lifecycle handler for the membership group read component.
 */
interface MembershipGroupReadLifecycleHandler : LifecycleEventHandler {
    /**
     * Default implementation.
     */
    class Impl(
        private val configurationReadService: ConfigurationReadService,
        private val membershipGroupReadSubscriptions: MembershipGroupReadSubscriptions,
        private val membershipGroupReadCache: MembershipGroupReadCache
    ) : MembershipGroupReadLifecycleHandler {
        companion object {
            val logger = contextLogger()
        }

        private var configRegistrationHandle: AutoCloseable? = null
        private var dependencyRegistrationHandle: RegistrationHandle? = null

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is StartEvent -> handleStartEvent(coordinator)
                is StopEvent -> handleStopEvent(coordinator)
                is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
                is ConfigChangedEvent -> handleConfigReceivedEvent(event, coordinator)
            }
        }

        /**
         * Start the cache and register to receive updates about dependency component statuses.
         */
        private fun handleStartEvent(coordinator: LifecycleCoordinator) {
            logger.trace(MembershipGroupReaderProvider::class.simpleName + " handling start event.")
            membershipGroupReadCache.start()
            dependencyRegistrationHandle?.close()
            dependencyRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        }

        /**
         * Stop this component by stopping subscriptions, clearing caches, and close any registration handles
         * which are open.
         */
        private fun handleStopEvent(coordinator: LifecycleCoordinator) {
            logger.trace(MembershipGroupReaderProvider::class.simpleName + " handling stop event.")
            coordinator.updateStatus(LifecycleStatus.DOWN, "Stopped component.")
            membershipGroupReadSubscriptions.stop()
            membershipGroupReadCache.stop()
            dependencyRegistrationHandle?.close()
            configRegistrationHandle?.close()
        }

        /**
         * React to the configuration read service going up/down.
         * Create a registration handle on the configuration read service when it is up and close it when it goes down.
         */
        private fun handleRegistrationChangeEvent(
            event: RegistrationStatusChangeEvent,
            coordinator: LifecycleCoordinator
        ) {
            logger.trace(MembershipGroupReaderProvider::class.simpleName + " handling registration changed event.")
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
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    configRegistrationHandle?.close()
                }
            }
        }

        /**
         * Process configuration changes by stopping this component, resetting cached data, recreating subscriptions and
         * finally starting the component again.
         */
        private fun handleConfigReceivedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
            logger.trace(MembershipGroupReaderProvider::class.simpleName + " handling new config event.")
            coordinator.updateStatus(LifecycleStatus.DOWN, "Started processing updated configuration.")
            membershipGroupReadSubscriptions.stop()
            membershipGroupReadCache.stop()
            membershipGroupReadCache.start()
            membershipGroupReadSubscriptions.start(event.config.toMessagingConfig())
            coordinator.updateStatus(LifecycleStatus.UP, "Finished processing updated configuration.")
        }
    }
}
