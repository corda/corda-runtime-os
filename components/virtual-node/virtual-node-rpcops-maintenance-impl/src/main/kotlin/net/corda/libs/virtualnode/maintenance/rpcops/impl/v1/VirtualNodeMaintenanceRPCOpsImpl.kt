package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.data.chunking.PropertyKeys
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Suppress("unused")
@Component(service = [PluggableRPCOps::class])
class VirtualNodeMaintenanceRPCOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = VirtualNodeSenderFactory::class)
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
) : VirtualNodeMaintenanceRPCOps, PluggableRPCOps<VirtualNodeMaintenanceRPCOps>, Lifecycle {

    companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RPC_CONFIG)
        private val logger = contextLogger()

        private const val REGISTRATION = "REGISTRATION"
        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
    }

    override val targetInterface: Class<VirtualNodeMaintenanceRPCOps> = VirtualNodeMaintenanceRPCOps::class.java
    override val protocolVersion: Int = 1

    private val clock = UTCClock()
    private val dependentComponents = DependentComponents.of(
        ::cpiUploadRPCOpsService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeMaintenanceRPCOps>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
                logger.info("${this::javaClass.name} is now Up")
            }
            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN)
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }
                    LifecycleStatus.UP -> {
                        // Receive updates to the RPC and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
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
                    coordinator.createManagedResource(SENDER) {
                        virtualNodeSenderFactory.createSender(duration, messagingConfig)
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            }
        }
    }

    override fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRPCOps.CpiUploadResponse {
        logger.info("Force uploading CPI: ${upload.fileName}")
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
        val cpiUploadRequestId = cpiUploadRPCOpsService.cpiUploadManager.uploadCpi(
            upload.fileName, upload.content,
            mapOf(PropertyKeys.FORCE_UPLOAD to true.toString())
        )
        return CpiUploadRPCOps.CpiUploadResponse(cpiUploadRequestId.requestId)
    }

    /**
     * Sends the [request] to the virtual topic on bus.
     *
     * @property request is a [VirtualNodeManagementRequest]. This an envelope around the intended request
     * @throws CordaRuntimeException If the sender wasn't initialized or the request fails.
     * @return [VirtualNodeManagementResponse] which is an envelope around the actual response.
     *  This response corresponds to the [VirtualNodeManagementRequest] received by the function
     * @see VirtualNodeManagementRequest
     * @see VirtualNodeManagementResponse
     */
    @Suppress("ThrowsCount")
    private fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )

        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
            ?: throw CordaRuntimeException("Sender not initialized, check component status for ${this.javaClass.name}")

        return sender.sendAndReceive(request)
    }

    // Lookup and update the virtual node for the given virtual node short ID.
    //  This will update the last instance of said virtual node, sorted by CPI version
    @Suppress("ForbiddenComment")
    override fun updateVirtualNodeState(
        virtualNodeShortId: String,
        newState: String
    ): ChangeVirtualNodeStateResponse {
        val instant = clock.instant()
        // Lookup actor to keep track of which RPC user triggered an update
        val actor = CURRENT_RPC_CONTEXT.get().principal
        logger.debug { "Received request to update state for $virtualNodeShortId to $newState by $actor at $instant" }
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
        // TODO: Validate newState
        // Send request for update to kafka, precessed by the db worker in VirtualNodeWriterProcessor
        val rpcRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeStateChangeRequest(
                virtualNodeShortId,
                newState,
                actor
            )
        )
        // Actually send request and await response message on bus
        val resp: VirtualNodeManagementResponse = sendAndReceive(rpcRequest)
        logger.debug { "Received response to update for $virtualNodeShortId to $newState by $actor" }

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeStateChangeResponse -> {
                resolvedResponse.run {
                    ChangeVirtualNodeStateResponse(holdingIdentityShortHash, virtualNodeState)
                }
            }
            is VirtualNodeManagementResponseFailure -> {
                val exception = resolvedResponse.exception
                if (exception == null) {
                    logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                    throw InternalServerException("Request was unsuccessful but no exception was provided.")
                }
                logger.warn(
                    "Remote request to update virtual node responded with exception of type " +
                        "${exception.errorType}: ${exception.errorMessage}"
                )
                throw InternalServerException(exception.errorMessage)
            }
            else -> throw UnknownMaintenanceResponseTypeException(resp.responseType::class.java.name)
        }
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.close()
}
