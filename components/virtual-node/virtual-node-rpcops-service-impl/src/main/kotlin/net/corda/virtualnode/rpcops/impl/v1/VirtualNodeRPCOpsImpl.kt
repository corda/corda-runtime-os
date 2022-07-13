package net.corda.virtualnode.rpcops.impl.v1

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.CpiIdentifier
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeParameters
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeResponse
import net.corda.libs.virtualnode.endpoints.v1.types.GetVirtualNodesResponse
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPVirtualNodeStateChangeResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.virtualnode.rpcops.impl.GROUP_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
/** An implementation of [VirtualNodeRPCOpsInternal]. */
@Suppress("Unused")
@Component(service = [VirtualNodeRPCOpsInternal::class, PluggableRPCOps::class], immediate = true)
// Primary constructor is for test. This is until a clock service is available
internal class VirtualNodeRPCOpsImpl @VisibleForTesting constructor(
    private val publisherFactory: PublisherFactory,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private var clock: Clock
) : VirtualNodeRPCOpsInternal, PluggableRPCOps<VirtualNodeRPCOps> {

    @Activate constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService
    ) : this(publisherFactory, virtualNodeInfoReadService, UTCClock())

    private companion object {
        // The configuration used for the RPC sender.
        private val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeManagementRequest::class.java,
            VirtualNodeManagementResponse::class.java
        )
        val logger = contextLogger()
    }

    override val targetInterface = VirtualNodeRPCOps::class.java
    override val protocolVersion = 1
    private var rpcSender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>? = null
    private var requestTimeout: Duration? = null
    override val isRunning get() = rpcSender?.isRunning ?: false && requestTimeout != null

    override fun start() = Unit

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }

    override fun createAndStartRpcSender(messagingConfig: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(rpcConfig, messagingConfig).apply { start() }
    }

    override fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    override fun createVirtualNode(request: CreateVirtualNodeParameters): CreateVirtualNodeResponse {
        val instant = clock.instant()
        validateX500Name(request.x500Name)

        val actor = CURRENT_RPC_CONTEXT.get().principal
        val rpcRequest = with(request) {
            VirtualNodeManagementRequest(
                instant,
                VirtualNodeCreateRequest(
                    x500Name,cpiFileChecksum, vaultDdlConnection, vaultDmlConnection, cryptoDdlConnection, cryptoDmlConnection, actor
                )
            )
        }
        val resp = sendRequest(rpcRequest)
        logger.info(resp.responseType.toString())

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeCreateResponse -> {
                CreateVirtualNodeResponse(
                    resolvedResponse.x500Name,
                    CpiIdentifier.fromAvro(resolvedResponse.cpiIdentifier),
                    resolvedResponse.cpiFileChecksum,
                    resolvedResponse.mgmGroupId,
                    resolvedResponse.holdingIdentifierHash,
                    resolvedResponse.vaultDdlConnectionId,
                    resolvedResponse.vaultDmlConnectionId,
                    resolvedResponse.cryptoDdlConnectionId,
                    resolvedResponse.cryptoDmlConnectionId,
                    resolvedResponse.virtualNodeState
                )
            }
            is VirtualNodeManagementResponseFailure -> {
                val exception = resolvedResponse.exception
                if (exception == null) {
                    logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                    throw InternalServerException("Request was unsuccessful but no exception was provided.")
                }
                logger.warn("Remote request to create virtual node responded with exception: ${exception.errorMessage}")
                throw InternalServerException(exception.errorMessage)
            }
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    override fun getAllVirtualNodes(): GetVirtualNodesResponse {
        return GetVirtualNodesResponse(virtualNodeInfoReadService.getAll())
    }

    override fun updateVirtualNodeState(
        virtualNodeShortId: String,
        cpiId: String,
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
                cpiId,
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

    /** Validates the [x500Name]. */
    private fun validateX500Name(x500Name: String) = try {
        MemberX500Name.parse(x500Name)
    } catch (e: Exception) {
        logger.warn("Configuration Management  X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}")
        val message = "X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}"
        throw InvalidInputDataException(message)
    }

    /**
     * Sends the [request] to the configuration management topic on Kafka.
     *
     * @throws VirtualNodeRPCOpsServiceException If the updated configuration could not be published.
     */
    @Suppress("ThrowsCount")
    private fun sendRequest(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        val nonNullRPCSender = rpcSender ?: throw VirtualNodeRPCOpsServiceException(
            "Configuration update request could not be sent as no RPC sender has been created."
        )
        val nonNullRequestTimeout = requestTimeout ?: throw VirtualNodeRPCOpsServiceException(
            "Configuration update request could not be sent as the request timeout has not been set."
        )
        return try {
            nonNullRPCSender.sendRequest(request).getOrThrow(nonNullRequestTimeout)
        } catch (e: Exception) {
            throw VirtualNodeRPCOpsServiceException("Could not complete virtual node creation request.", e)
        }
    }
}
