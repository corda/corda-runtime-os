package net.corda.libs.virtualnode.write.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.libs.virtualnode.write.VirtualNodeWriterException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro

/**
 * An RPC responder processor that handles virtual node creation requests.
 *
 * For each virtual node creation request, persists the created virtual node to the cluster database publishes it to
 * Kafka.
 *
 * @property publisher Used to publish to Kafka.
 * @property cpiRepository Temporary stub used for retrieving CPI data and holding identities.
 */
internal class VirtualNodeWriterProcessor(
    private val publisher: Publisher,
    private val cpiRepository: CPIRepository
) : RPCResponderProcessor<VirtualNodeCreationRequest, VirtualNodeCreationResponse> {

    /**
     * For each [request], the processor attempts to commit a new virtual node to the cluster database. If successful,
     * the created virtual node is then published by the [publisher] to the `VIRTUAL_NODE_INFO_TOPIC` topic.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(request: VirtualNodeCreationRequest, respFuture: VirtualNodeCreationResponseFuture) {
        val cpiMetadata = cpiRepository.getCPIMetadata(request.cpiIdHash)
        if (cpiMetadata == null) {
            val errMsg = "CPI with hash ${request.cpiIdHash} was not found."
            handleException(respFuture, errMsg, VirtualNodeWriterException::class.java.name, null, null)
            return
        }

        val holdingId = HoldingIdentity(request.x500Name, cpiMetadata.mgmGroupId)
        val storedHoldingId = cpiRepository.getHoldingIdentity(holdingId.id)
        if (storedHoldingId == null) {
            cpiRepository.putHoldingIdentity(request.x500Name, cpiMetadata.mgmGroupId)
        } else {
            // We check whether the non-null stored holding ID is different to the one we just constructed.
            if (storedHoldingId != holdingId) {
                val errMsg = "New holding identity $holdingId has a short hash that collided with existing holding " +
                        "identity $storedHoldingId."
                handleException(respFuture, errMsg, VirtualNodeWriterException::class.java.name, null, null)
                return
            }
        }

        // TODO - Write virtual node to database.

        val virtualNodeInfo = VirtualNodeInfo(holdingId.toAvro(), cpiMetadata.id.toAvro())
        val virtualNodeRecord = Record(VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity, virtualNodeInfo)
        val future = publisher.publish(listOf(virtualNodeRecord)).first()

        try {
            future.get()
        } catch (e: Exception) {
            val errMsg = "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e"
            handleException(respFuture, errMsg, e::class.java.name, cpiMetadata, holdingId)
            return
        }

        val response = VirtualNodeCreationResponse(
            true,
            null,
            request.x500Name,
            cpiMetadata.id.toAvro(),
            request.cpiIdHash,
            cpiMetadata.mgmGroupId,
            holdingId.toAvro(),
            holdingId.id
        )
        respFuture.complete(response)
    }

    /** Completes the [respFuture] with an [ExceptionEnvelope]. */
    @Suppress("LongParameterList")
    private fun handleException(
        respFuture: VirtualNodeCreationResponseFuture,
        errMsg: String,
        errClassName: String,
        cpiMetadata: CPIMetadata?,
        holdingId: HoldingIdentity?
    ): Boolean {
        val exception = ExceptionEnvelope(errClassName, errMsg)
        val response = VirtualNodeCreationResponse(
            false,
            exception,
            holdingId?.x500Name,
            cpiMetadata?.id?.toAvro(),
            cpiMetadata?.idShortHash,
            holdingId?.groupId,
            holdingId?.toAvro(),
            holdingId?.id
        )
        return respFuture.complete(response)
    }
}