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
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
internal class ConfigWriteEventHandler(
    private val rpcSubscriptionFactory: RPCSubscriptionFactory,
    private val configMerger: ConfigMerger) : LifecycleEventHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var rpcSubscription: ConfigurationManagementRPCSubscription? = null

    private var registration: RegistrationHandle? = null

    private var bootstrapConfig: SmartConfig? = null

    /**
     * Upon [BootstrapConfigEvent], populates boot config and waits on needed components. On components being ready,
     * it starts processing cluster configuration updates. Upon [StopEvent], stops processing them.
     *
     * @throws ConfigWriteServiceException If multiple [BootstrapConfigEvent]s are received, or if the creation of the
     *  subscription fails.
     */
    @Suppress("NestedBlockDepth")
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is BootstrapConfigEvent -> {
                logger.debug { "Bootstrap config provided" }
                if (bootstrapConfig != null) {
                    val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                            "different config. Current: $bootstrapConfig, New: ${event.bootstrapConfig}"
                    logger.error(errorString)
                    throw ConfigWriteServiceException(errorString)
                }
                bootstrapConfig = event.bootstrapConfig
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                        LifecycleCoordinatorName.forComponent<ConfigPublishService>()
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    if (rpcSubscription == null) {
                        rpcSubscription =
                            rpcSubscriptionFactory.create(configMerger.getMessagingConfig(bootstrapConfig!!))
                                .apply { start() }
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                } else {
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }

            is StopEvent -> {
                rpcSubscription?.close()
                rpcSubscription = null
            }
        }
    }
}
