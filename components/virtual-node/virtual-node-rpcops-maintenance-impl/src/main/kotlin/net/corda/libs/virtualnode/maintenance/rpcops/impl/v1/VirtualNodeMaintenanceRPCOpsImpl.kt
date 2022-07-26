package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

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
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component(service = [PluggableRPCOps::class])
class VirtualNodeMaintenanceRPCOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = VirtualNodeSenderService::class)
    private val virtualNodeSenderService: VirtualNodeSenderService,
) : VirtualNodeMaintenanceRPCOps, PluggableRPCOps<VirtualNodeMaintenanceRPCOps>, Lifecycle {

    companion object {
        private val logger = contextLogger()
    }

    private val clock = UTCClock()
    private val dependentComponents = DependentComponents.of(
        ::cpiUploadRPCOpsService,
        ::virtualNodeSenderService
    )

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeMaintenanceRPCOps>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        logger.info(event.toString())
        logger.info(coordinator.toString())
        when (event) {
            is StartEvent -> dependentComponents.registerAndStartAll(coordinator)
        }
    }

    override val protocolVersion: Int = 1

    override val targetInterface: Class<VirtualNodeMaintenanceRPCOps> = VirtualNodeMaintenanceRPCOps::class.java

    override fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRPCOps.UploadResponse {
        logger.info("Force uploading CPI: ${upload.fileName}")
        if (!isRunning) throw IllegalStateException("${this.javaClass.simpleName} is not running! Its status is: ${coordinator.status}")
        val cpiUploadRequestId = cpiUploadRPCOpsService.cpiUploadManager.uploadCpi(
            upload.fileName, upload.content,
            mapOf(PropertyKeys.FORCE_UPLOAD to true.toString())
        )
        return CpiUploadRPCOps.UploadResponse(cpiUploadRequestId.requestId)
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
        if (!isRunning) throw IllegalStateException("${this.javaClass.simpleName} is not running! Its status is: ${coordinator.status}")
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
        val resp: VirtualNodeManagementResponse = virtualNodeSenderService.sendAndReceive(rpcRequest)
        logger.debug { "Received response to update for $virtualNodeShortId to $newState by $actor" }

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeStateChangeResponse -> {
                ChangeVirtualNodeStateResponse(
                    resolvedResponse.holdingIdentityShortHash,
                    resolvedResponse.virtualNodeState
                )
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
    override val isRunning get() = coordinator.isRunning
    override fun start() = coordinator.start()
    override fun stop() = coordinator.close()
}
