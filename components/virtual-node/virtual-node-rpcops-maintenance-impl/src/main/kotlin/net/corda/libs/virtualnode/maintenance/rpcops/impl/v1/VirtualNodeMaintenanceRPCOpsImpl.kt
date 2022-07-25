package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

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
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
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
    configReadService: ConfigurationReadService,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    private val clock: Clock = UTCClock()
) : VirtualNodeMaintenanceRPCOps, PluggableRPCOps<VirtualNodeMaintenanceRPCOps>, Lifecycle {

    companion object {
        /** The configuration used for the RPC sender. */
        private const val GROUP_NAME = "virtual.node.management"
        private const val CLIENT_NAME_HTTP = "virtual.node.manager.http"
        private val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            Schemas.VirtualNode.VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeManagementRequest::class.java,
            VirtualNodeManagementResponse::class.java
        )
        private val logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeMaintenanceRPCOps>(
        VirtualNodeMaintenanceRPCOpsHandler(configReadService, this)
    )

    override val protocolVersion: Int = 1

    override val targetInterface: Class<VirtualNodeMaintenanceRPCOps> = VirtualNodeMaintenanceRPCOps::class.java

    // Factory generated instance of the RPCSender, represents a pubsub of <RequestType, ResponseType>.
    //  Set/updated from VirtualNodeMaintenanceRPCOpsHandler as a result of lifecycle events.
    private var rpcSender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>? = null
    // Request timeout for kafka operations.
    //  All are set from VirtualNodeMaintenanceRPCOpsHandler.
    private var requestTimeout: Duration? = null

    override val isRunning get() = coordinator.isRunning

    private val cpiUploadManager get() = cpiUploadRPCOpsService.cpiUploadManager

    override fun start() {
        coordinator.start()
    }

    fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    fun configureRPCSender(messagingConfig: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(rpcConfig, messagingConfig).apply { start() }
    }

    override fun stop() {
        coordinator.close()
        rpcSender?.close()
        rpcSender = null
    }

    private fun requireRunning() {
        if (!isRunning) {
            throw IllegalStateException("${this.javaClass.simpleName} is not running! Its status is: ${coordinator.status}")
        }
    }

    override fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRPCOps.UploadResponse {
        logger.info("Force uploading CPI: ${upload.fileName}")
        requireRunning()
        val cpiUploadRequestId = cpiUploadManager.uploadCpi(
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
                logger.warn("Remote request to update virtual node responded with exception of type " +
                        "${exception.errorType}: ${exception.errorMessage}")
                throw InternalServerException(exception.errorMessage)
            }
            else -> throw UnknownMaintenanceResponseTypeException(resp.responseType::class.java.name)
        }
    }

    /**
     * Sends the [request] to the configuration management topic on bus.
     *
     * @throws VirtualNodeRPCOpsServiceException If the updated configuration could not be published.
     */
    @Suppress("ThrowsCount")
    private fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        // Ensure rpcSender is set up.
        val nonNullRPCSender = rpcSender ?: throw VirtualNodeRPCMaintenanceOpsServiceException(
            "Configuration update request could not be sent as no RPC sender has been created."
        )
        val nonNullRequestTimeout = requestTimeout ?: throw VirtualNodeRPCMaintenanceOpsServiceException(
            "Configuration update request could not be sent as the request timeout has not been set."
        )
        return try {
            // Attempt to put request on the bus.
            //  Auto fail if timeout window exceeded
            nonNullRPCSender.sendRequest(request).getOrThrow(nonNullRequestTimeout)
        } catch (e: Exception) {
            throw VirtualNodeRPCMaintenanceOpsServiceException("Could not complete virtual node creation request.", e)
        }
    }
}
