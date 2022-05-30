package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.writer.ConfigurationManagementRPCSubscription
import net.corda.configuration.write.impl.writer.RPCSubscriptionFactory
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
internal class ConfigWriteEventHandler(
    private val rpcSubscriptionFactory: RPCSubscriptionFactory,
    private val configMerger: ConfigMerger) : LifecycleEventHandler {
    companion object {
        val logger = contextLogger()
    }

    private var rpcSubscription: ConfigurationManagementRPCSubscription? = null

    private var registration: RegistrationHandle? = null

    private var bootStrapConfig: SmartConfig? = null

    /**
     * Upon [BootstrapConfigEvent], starts processing cluster configuration updates. Upon [StopEvent], stops processing
     * them.
     *
     * @throws ConfigWriteServiceException If multiple [BootstrapConfigEvent]s are received, or if the creation of the
     *  subscription fails.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is BootstrapConfigEvent -> {
                logger.info("Bootstrap config provided")
                bootStrapConfig = event.bootConfig
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                        LifecycleCoordinatorName.forComponent<ConfigPublishService>()
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Switching to ${event.status}")
                    if (rpcSubscription == null) {
                        // TODO - CORE-3316 - At worker start-up, read back configuration from database and check it
                        //  against Kafka topic.
                        rpcSubscription =
                            rpcSubscriptionFactory.create(configMerger.getMessagingConfig(bootStrapConfig!!))
                                .apply { start() }
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                } else {
                    logger.warn("Switching to ${event.status}")
                    coordinator.updateStatus(event.status)
                }
            }

            is StopEvent -> {
                rpcSubscription?.stop()
                rpcSubscription = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }
}