package net.corda.virtualnode.rpcops.common.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

/**
 * Wrapper service around the publisher factory and RPCSender lifecycle
 *
 * Adds a lifecycle around the existence of the RPC Sender. This allows the lifecycle
 * logic of the sender to be reused by the [VirtualNodeRPCOps] and the [VirtualNodeMaintenanceRPCOps]
 *
 * @constructor Primary constructor is visible for testing - this allows the option of injecting mocks
 *  at test time without requiring OSGI.
 * @author Ben McMahon
 */

@Component(service = [VirtualNodeSenderService::class])
class VirtualNodeSenderServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = RPCSenderFactory::class)
    private val rpcSenderFactory: RPCSenderFactory,
) : VirtualNodeSenderService {

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RPC_CONFIG)
        private val logger = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeSenderService>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                coordinator.createManagedResource("REGISTRATION") {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
            }
            is StopEvent -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf("CONFIG_HANDLE"))
                        coordinator.postEvent(StopEvent(errored = true))
                    }
                    LifecycleStatus.UP -> {
                        // Receive updates to the RPC and Messaging config
                        coordinator.createManagedResource("CONFIG_HANDLE") {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }
                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status)
                logger.info("${this::javaClass.name} is now ${event.status}")
            }
            is ConfigChangedEvent -> {
                if (requiredKeys.all { it in event.config.keys } and event.keys.any { it in requiredKeys }) {
                    val rpcConfig = event.config.getConfig(ConfigKeys.RPC_CONFIG)
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    val duration = Duration.ofMillis(rpcConfig.getInt(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS).toLong())
                    // Make sender unavailable while we're updating
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    coordinator.createManagedResource("SENDER") {
                        rpcSenderFactory.createSender(duration, messagingConfig)
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            }
        }
    }

    /**
     * Sends the [request] to the configuration management topic on bus.
     *
     * @property request is a [VirtualNodeManagementRequest]. This an enveloper around the intended request
     * @throws CordaRuntimeException If the updated configuration could not be published.
     * @return [VirtualNodeManagementResponse] which is an envelope around the actual response.
     *  This response corresponds to the [VirtualNodeManagementRequest] received by the function
     * @see VirtualNodeManagementRequest
     * @see VirtualNodeManagementResponse
     */
    @Suppress("ThrowsCount")
    override fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )

        return lifecycleCoordinator.getManagedResource<RPCSenderWrapperImpl>("SENDER")!!.sendAndReceive(request)
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
}
