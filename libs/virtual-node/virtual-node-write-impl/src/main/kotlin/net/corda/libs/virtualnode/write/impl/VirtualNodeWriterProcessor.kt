package net.corda.libs.virtualnode.write.impl

import net.corda.data.crypto.SecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import java.nio.ByteBuffer

/**
 * An RPC responder processor that handles virtual node creation requests.
 *
 * For each virtual node creation request, persists the created virtual node to the cluster database publishes it to
 * Kafka.
 *
 * @property publisher Used to publish to Kafka.
 */
internal class VirtualNodeWriterProcessor(
    private val publisher: Publisher
) : RPCResponderProcessor<VirtualNodeCreationRequest, VirtualNodeCreationResponse> {

    /**
     * For each [request], the processor attempts to commit a new virtual node to the cluster database. If successful,
     * the created virtual node is then published by the [publisher] to the `VIRTUAL_NODE_INFO_TOPIC` topic.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    // TODO - Process incoming requests to actually create new virtual nodes.
    override fun onNext(request: VirtualNodeCreationRequest, respFuture: VirtualNodeCreationResponseFuture) {
        val cpiIdHash = SecureHash("algorithm", ByteBuffer.wrap("1234".toByteArray()))
        val cpiId = CPIIdentifier("", "", cpiIdHash)
        val holdingId = HoldingIdentity("", "")
        val response = VirtualNodeCreationResponse(
            true, null, "", cpiId, "", "", holdingId, "dummyHoldingIdHash"
        )
        respFuture.complete(response)
    }
}