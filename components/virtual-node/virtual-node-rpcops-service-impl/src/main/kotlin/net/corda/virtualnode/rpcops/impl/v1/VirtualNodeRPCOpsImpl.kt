package net.corda.virtualnode.rpcops.impl.v1

import net.corda.data.crypto.SecureHash
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.HttpApiException
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.CpiIdentifier
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.CLIENT_NAME_HTTP
import net.corda.virtualnode.rpcops.impl.GROUP_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
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
    }

    override val targetInterface = VirtualNodeRPCOps::class.java
    override val protocolVersion = 1
    private var rpcSender: RPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>? = null
    private var requestTimeout: Duration? = null
    override val isRunning get() = rpcSender != null && requestTimeout != null

    override fun start() = Unit

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }

    override fun createAndStartRPCSender(config: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(RPC_CONFIG, config).apply { start() }
    }

    override fun setTimeout(millis: Int) {
        this.requestTimeout = Duration.ofMillis(millis.toLong())
    }

    override fun createVirtualNode(request: HTTPCreateVirtualNodeRequest): HTTPCreateVirtualNodeResponse {
        val cpiIdHash = SecureHash("algorithm", ByteBuffer.wrap("1234".toByteArray()))
        val rpcRequest = VirtualNodeCreationRequest("dummyX500Name", cpiIdHash)
        val response =  sendRequest(rpcRequest)

        return if (response.success) {
            val cpiId = CpiIdentifier("", "", "")
            HTTPCreateVirtualNodeResponse("", cpiId, "", "", "")
        } else {
            val exception = response.exception
                ?: throw HttpApiException("Request was unsuccessful but no exception was provided.", 500)
            // TODO - CORE-3304 - Return richer exception (e.g. containing the config and version currently in the DB).
            throw HttpApiException("${exception.errorType}: ${exception.errorMessage}", 500)
        }
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
            throw VirtualNodeRPCOpsServiceException("Could not publish updated configuration.", e)
        }
    }
}