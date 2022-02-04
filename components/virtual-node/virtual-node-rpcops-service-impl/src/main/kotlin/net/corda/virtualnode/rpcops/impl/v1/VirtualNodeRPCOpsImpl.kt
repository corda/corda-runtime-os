package net.corda.virtualnode.rpcops.impl.v1

import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.CPIIdentifier
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.identity.MemberX500Name
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
internal class VirtualNodeRPCOpsImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : VirtualNodeRPCOpsInternal, PluggableRPCOps<VirtualNodeRPCOps> {
    private companion object {
        // The configuration used for the RPC sender.
        private val RPC_CONFIG = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_HTTP,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeCreationRequest::class.java,
            VirtualNodeCreationResponse::class.java
        )
        val logger = contextLogger()
    }

    override val targetInterface = VirtualNodeRPCOps::class.java
    override val protocolVersion = 1
    private var rpcSender: RPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>? = null
    private var requestTimeout: Duration? = null
    override val isRunning get() = rpcSender?.isRunning ?: false && requestTimeout != null

    override fun start() = Unit

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }

    override fun createAndStartRpcSender(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG, config).apply { start() }
    }

    override fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    override fun createVirtualNode(request: HTTPCreateVirtualNodeRequest): HTTPCreateVirtualNodeResponse {
        val rpcRequest = VirtualNodeCreationRequest(request.x500Name, request.cpiIdHash)
        validateX500Name(rpcRequest.x500Name)
        val resp = sendRequest(rpcRequest)

        return if (resp.success) {
            val cpiId = CPIIdentifier.fromAvro(resp.cpiIdentifier)
            HTTPCreateVirtualNodeResponse(
                resp.x500Name, cpiId, resp.cpiIdentifierHash, resp.mgmGroupId, resp.holdingIdentifierHash
            )
        } else {
            val exception = resp.exception
            if (exception == null) {
                logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                throw InternalServerException("Request was unsuccessful but no exception was provided.")
            }
            logger.warn("Remote request to create virtual node responded with exception: ${exception.errorType}: ${exception.errorMessage}")
            throw InternalServerException("${exception.errorType}: ${exception.errorMessage}")
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
    private fun sendRequest(request: VirtualNodeCreationRequest): VirtualNodeCreationResponse {
        val nonNullRPCSender = rpcSender ?: throw VirtualNodeRPCOpsServiceException(
            "Configuration update request could not be sent as no RPC sender has been created."
        )
        val nonNullRequestTimeout = requestTimeout ?: throw VirtualNodeRPCOpsServiceException(
            "Configuration update request could not be sent as the request timeout has not been set."
        )
        return try {
            nonNullRPCSender.sendRequest(request).getOrThrow(nonNullRequestTimeout)
        } catch (e: Exception) {
            throw VirtualNodeRPCOpsServiceException("Could not create virtual node.", e)
        }
    }
}