package net.corda.virtualnode.rpcops.common.impl

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import java.time.Duration

class VirtualNodeSenderImpl(
    override val timeout: Duration,
    private val sender: RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>
) : VirtualNodeSender {
    companion object {
        private val logger = contextLogger()
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
            logger.error("Could not complete virtual node creation request.", e)
            throw CordaRuntimeException("Could not complete virtual node creation request.", e)
        }
    }

    override fun close() = sender.close()
}
