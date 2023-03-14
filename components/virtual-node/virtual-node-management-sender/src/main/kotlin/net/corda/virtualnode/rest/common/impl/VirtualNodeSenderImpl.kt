package net.corda.virtualnode.rest.common.impl

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.rest.common.VirtualNodeSender
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class VirtualNodeSenderImpl(
    override val timeout: Duration,
    private val sender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>,
    private val asyncOperationPublisher: Publisher,
) : VirtualNodeSender {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val PUBLICATION_TIMEOUT_SECONDS = 180L
    }

    /**
     * Sends the [request] to the virtual node topic in the message bus.
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
        return try {
            sender.sendRequest(request).getOrThrow(timeout)
        } catch (e: Exception) {
            logger.warn("Could not complete virtual node management request.", e)
            throw CordaRuntimeException("Could not complete virtual node management request.", e)
        }
    }

    /**
     * Send asynchronous virtual node request and ensure publish succeeds.
     *
     * @param key the key for this request
     * @param request the asynchronous virtual node operation request
     */
    @Suppress("SpreadOperator")
    override fun sendAsync(key: String, request: VirtualNodeAsynchronousRequest) {
        val publish = asyncOperationPublisher.publish(
            listOf(Record(VIRTUAL_NODE_ASYNC_REQUEST_TOPIC, key, request))
        )
        try {
            CompletableFuture.allOf(*publish.toTypedArray()).get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw CordaRuntimeException("Could not publish asynchronous virtual node request.", e)
        }
    }

    override fun close() {
        sender.close()
        asyncOperationPublisher.close()
    }
}
