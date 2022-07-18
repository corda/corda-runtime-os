package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpi.upload.endpoints.v1.CpiUploadRPCOpsImpl.Companion.logger
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
import net.corda.libs.virtualnode.maintenance.endpoints.v1.types.HTTPVirtualNodeStateChangeResponse
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Suppress("unused")
@Component(service = [PluggableRPCOps::class])
class VirtualNodeMaintenanceRPCOpsImpl @VisibleForTesting constructor(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val configReadService: ConfigurationReadService,
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    private val publisherFactory: PublisherFactory,
    private val clock: Clock
) : VirtualNodeMaintenanceRPCOps, PluggableRPCOps<VirtualNodeMaintenanceRPCOps>, Lifecycle {

    @Activate constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configReadService: ConfigurationReadService,
        @Reference(service = CpiUploadRPCOpsService::class)
        cpiUploadRPCOpsService: CpiUploadRPCOpsService,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory
    ) : this(coordinatorFactory, configReadService, cpiUploadRPCOpsService, publisherFactory, UTCClock())

    private var rpcSender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>? = null

    companion object {
        // The configuration used for the RPC sender.
        private val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            Schemas.VirtualNode.VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeManagementRequest::class.java,
            VirtualNodeManagementResponse::class.java
        )
        val logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeMaintenanceRPCOps>(
        VirtualNodeMaintenanceRPCOpsHandler(configReadService, this)
    )

    override val protocolVersion: Int = 1

    override val targetInterface: Class<VirtualNodeMaintenanceRPCOps> = VirtualNodeMaintenanceRPCOps::class.java

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

    override fun updateVirtualNodeState(
        virtualNodeShortId: String,
        newState: String
    ): HTTPVirtualNodeStateChangeResponse {
        logger.info(virtualNodeShortId)
        val instant = clock.instant()
        // Validate newState

        val actor = CURRENT_RPC_CONTEXT.get().principal
        val rpcRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeStateChangeRequest(
                virtualNodeShortId,
                newState,
                actor
            )
        )
        val resp = sendRequest(rpcRequest)
        logger.info(resp.responseType.toString())

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeStateChangeResponse -> {
                HTTPVirtualNodeStateChangeResponse(
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
                logger.warn("Remote request to update virtual node responded with exception of type ${exception.errorType}: ${exception.errorMessage}")
                throw InternalServerException(exception.errorMessage)
            }
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    /**
     * Sends the [request] to the configuration management topic on Kafka.
     *
     * @throws VirtualNodeRPCOpsServiceException If the updated configuration could not be published.
     */
    @Suppress("ThrowsCount")
    private fun sendRequest(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        val nonNullRPCSender = rpcSender ?: throw VirtualNodeRPCMaintenanceOpsServiceException(
            "Configuration update request could not be sent as no RPC sender has been created."
        )
        val nonNullRequestTimeout = requestTimeout ?: throw VirtualNodeRPCMaintenanceOpsServiceException(
            "Configuration update request could not be sent as the request timeout has not been set."
        )
        return try {
            nonNullRPCSender.sendRequest(request).getOrThrow(nonNullRequestTimeout)
        } catch (e: Exception) {
            throw VirtualNodeRPCMaintenanceOpsServiceException("Could not complete virtual node creation request.", e)
        }
    }
}
