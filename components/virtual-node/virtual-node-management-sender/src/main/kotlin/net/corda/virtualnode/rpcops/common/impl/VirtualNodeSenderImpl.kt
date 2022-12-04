package net.corda.virtualnode.rpcops.common.impl

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC

class VirtualNodeSenderImpl(
    override val timeout: Duration,
    private val rpcSender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>,
    private val asyncOperationPublisher: Publisher,
) : VirtualNodeSender {

    private companion object {
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
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
            rpcSender.sendRequest(request).getOrThrow(timeout)
        } catch (e: Exception) {
            throw CordaRuntimeException("Could not complete virtual node management request.", e)
        }
    }

    /**
     * Send asynchronous virtual node request and ensure publish succeeds.
     */
    override fun sendAsync(key: String, request: VirtualNodeAsynchronousRequest) {
        val publish = asyncOperationPublisher.publish(listOf(
            Record(VIRTUAL_NODE_ASYNC_REQUEST_TOPIC, key, request)
        ))
        try {
            CompletableFuture.allOf(*publish.toTypedArray()).get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw CordaRuntimeException("Could not publish asynchronous virtual node request.", e)
        }
    }

    override fun close() = rpcSender.close()
}
