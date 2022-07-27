package net.corda.virtualnode.rpcops.common

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Component(service = [VirtualNodeSenderService::class])
class VirtualNodeSenderServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    var sender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>? = null,
    var timeout: Duration? = null
) : VirtualNodeSenderService {
    private companion object {
        private const val GROUP_NAME = "virtual.node.management"
        private const val CLIENT_NAME_HTTP = "virtual.node.manager.http"
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RPC_CONFIG)
        private val logger = contextLogger()
    }
    private var configReadServiceRegistrationHandle: AutoCloseable? = null
    private var configUpdateHandle: AutoCloseable? = null

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeSenderService>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                configReadServiceRegistrationHandle?.close()
                configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
                    setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
                )
            }
            is StopEvent -> {
                configReadServiceRegistrationHandle?.close()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> coordinator.postEvent(StopEvent(errored = true))
                    LifecycleStatus.UP -> {
                        // Receive updates to the RPC and Messaging config
                        configUpdateHandle?.close()
                        configUpdateHandle =
                            configurationReadService.registerComponentForUpdates(coordinator, requiredKeys)
                    }
                    else -> Unit
                }
                coordinator.updateStatus(event.status)
                logger.info("${this::javaClass.name} is now ${event.status}")
            }
            is ConfigChangedEvent -> onConfigChange(coordinator, event.config, event.keys)
        }
    }

    private fun onConfigChange(coordinator: LifecycleCoordinator, config: Map<String, SmartConfig>, changedKeys: Set<String>) {
        if (requiredKeys.all { it in config.keys } and changedKeys.any { it in requiredKeys }) {
            val rpcConfig = config.getConfig(ConfigKeys.RPC_CONFIG)
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            // Make sender unavailable while we're updating
            coordinator.updateStatus(LifecycleStatus.DOWN)
            try {
                timeout = Duration.ofMillis(rpcConfig.getInt(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS).toLong())
                // Attempt to create and start the sender
                sender = publisherFactory.createRPCSender(
                    RPCConfig(
                        GROUP_NAME,
                        CLIENT_NAME_HTTP,
                        Schemas.VirtualNode.VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
                        VirtualNodeManagementRequest::class.java,
                        VirtualNodeManagementResponse::class.java
                    ),
                    messagingConfig
                ).apply {
                    // Report as back up post start
                    start()
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            } catch (e: Exception) {
                logger.error("Exception was thrown while attempting to set up the sender or its timeout: $e")
                // Exception will implicitly perform coordinator.updateStatus(LifecycleStatus.ERROR)
                throw CordaRuntimeException("Exception was thrown while attempting to set up the sender or its timeout", e)
            }
        }
    }

    /**
     * Sends the [request] to the configuration management topic on bus.
     *
     * @throws CordaRuntimeException If the updated configuration could not be published.
     */
    @Suppress("ThrowsCount")
    override fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
        return try {
            sender!!.sendRequest(request).getOrThrow(timeout!!)
        } catch (e: Exception) {
            throw CordaRuntimeException("Could not complete virtual node creation request.", e)
        }
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning: Boolean get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
}
