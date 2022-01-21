package net.corda.libs.virtualnode.write.impl

import net.corda.data.crypto.SecureHash
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import java.nio.ByteBuffer

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests over RPC. Persists the updated configuration to the cluster database
 * and publishes the updated configuration to Kafka.
 *
 * @property publisher The publisher used to publish to Kafka.
 */
internal class VirtualNodeWriterProcessor(
    private val publisher: Publisher
) : RPCResponderProcessor<VirtualNodeCreationRequest, VirtualNodeCreationResponse> {

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database. If successful,
     * the updated config is then published by the [publisher] to the [CONFIG_TOPIC] topic for consumption using a
     * `ConfigReader`.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(request: VirtualNodeCreationRequest, respFuture: VirtualNodeCreationResponseFuture) {
        println("JJJ received request: $request")
        val cpiIdHash = SecureHash("algorithm", ByteBuffer.wrap("1234".toByteArray()))
        val cpiId = CPIIdentifier("", "", cpiIdHash)
        val holdingIdHash = SecureHash("algorithm", ByteBuffer.wrap("1234".toByteArray()))
        val response = VirtualNodeCreationResponse(
            true, null, "", cpiId, cpiIdHash, "", holdingIdHash
        )
        respFuture.complete(response)
    }
}