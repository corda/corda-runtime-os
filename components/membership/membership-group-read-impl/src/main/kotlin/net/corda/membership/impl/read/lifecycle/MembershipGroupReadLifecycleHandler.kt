package net.corda.membership.impl.read.lifecycle

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.config.MembershipConfig
import net.corda.membership.config.MembershipConfigConstants
import net.corda.membership.impl.config.MembershipConfigImpl
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.component.MembershipGroupReaderProviderImpl
import net.corda.membership.impl.read.subscription.MembershipGroupReadSubscriptions
import net.corda.membership.lifecycle.MembershipConfigReceived
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent

/**
 * Lifecycle handler for the membership group read component.
 */
interface MembershipGroupReadLifecycleHandler : LifecycleEventHandler {
    /**
     * Default implementation.
     */
    class Impl(
        private val membershipGroupReadService: MembershipGroupReaderProviderImpl,
        private val membershipGroupReadSubscriptions: MembershipGroupReadSubscriptions,
        private val membershipGroupReadCache: MembershipGroupReadCache
    ) : MembershipGroupReadLifecycleHandler {
        private var configRegistrationHandle: AutoCloseable? = null
        private var componentRegistrationHandle: AutoCloseable? = null

        private val virtualNodeInfoReader
            get() = membershipGroupReadService.virtualNodeInfoReader

        private val cpiInfoReader
            get() = membershipGroupReadService.cpiInfoReader

        private val configurationReadService
            get() = membershipGroupReadService.configurationReadService

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is StartEvent -> handleStartEvent(coordinator)
                is StopEvent -> handleStopEvent()
                is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
                is MembershipConfigReceived -> handleConfigReceivedEvent(event, coordinator)
            }
        }

        /**
         * Start all necessary components and register to receive updates about their status.
         * Caches and subscriptions are created when configuration is received.
         */
        private fun handleStartEvent(coordinator: LifecycleCoordinator) {
            configurationReadService.start()
            cpiInfoReader.start()
            virtualNodeInfoReader.start()
            membershipGroupReadCache.start()

            componentRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
                    LifecycleCoordinatorName.forComponent<VirtualNodeInfoReaderComponent>(),
                )
            )
        }

        /**
         * Stop this component by stopping all components it uses which has lifecycle handling, stop subscriptions, clear
         * caches, and close any registration handles which are open.
         */
        private fun handleStopEvent() {
            configurationReadService.stop()
            cpiInfoReader.stop()
            virtualNodeInfoReader.stop()
            membershipGroupReadSubscriptions.stop()
            membershipGroupReadCache.stop()
            componentRegistrationHandle?.close()
            configRegistrationHandle?.close()
        }

        /**
         * If components this component depends on go down, then stop this component also.
         * The if the components start again, start this component also and subscribe to configuration changes.
         */
        private fun handleRegistrationChangeEvent(
            event: RegistrationStatusChangeEvent,
            coordinator: LifecycleCoordinator
        ) {
            // Respond to config read service lifecycle status change
            when (event.status) {
                LifecycleStatus.UP -> {
                    configRegistrationHandle = configurationReadService.registerForUpdates(
                        MembershipGroupConfigurationHandler(coordinator)
                    )
                    coordinator.updateStatus(LifecycleStatus.UP)
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
        private fun handleConfigReceivedEvent(event: MembershipConfigReceived, coordinator: LifecycleCoordinator) {
            coordinator.updateStatus(LifecycleStatus.DOWN, "Started processing updated configuration.")
            if (membershipGroupReadService.isRunning) {
                membershipGroupReadCache.stop()
                membershipGroupReadSubscriptions.stop()
            }
            membershipGroupReadCache.start()
            membershipGroupReadSubscriptions.start(event.config)
            coordinator.updateStatus(LifecycleStatus.UP, "Finished processing updated configuration.")
        }

        /**
         * Parse membership config from received config and pass the new config to the service lifecycle coordinator.
         */
        class MembershipGroupConfigurationHandler(
            val coordinator: LifecycleCoordinator
        ) : ConfigurationHandler {
            override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
                if (MembershipConfigConstants.CONFIG_KEY in changedKeys) {
                    val membershipConfig = with(config[MembershipConfigConstants.CONFIG_KEY]) {
                        when {
                            this == null || isEmpty -> handleEmptyMembershipConfig()
                            else -> MembershipConfigImpl(root().unwrapped())
                        }
                    }
                    coordinator.postEvent(MembershipConfigReceived(membershipConfig))
                }
            }

            private fun handleEmptyMembershipConfig(): MembershipConfig {
                throw IllegalStateException("Configuration '${MembershipConfigConstants.CONFIG_KEY}' is empty")
            }
        }
    }
}