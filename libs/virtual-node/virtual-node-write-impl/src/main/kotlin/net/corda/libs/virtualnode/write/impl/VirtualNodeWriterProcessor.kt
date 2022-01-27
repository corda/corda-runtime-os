package net.corda.libs.virtualnode.write.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.data.virtualnode.VirtualNodeInfo
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
    override fun onNext(request: VirtualNodeCreationRequest, respFuture: VirtualNodeCreationResponseFuture) {
        // TODO - Write vnode to database.
        // TODO - Retrieve CPI from database.
        // TODO - Wrap retrieving these in functions.
        // TODO - Handle possible exceptions in flow chart (not-unique holding ID, invalid CPI).
        val cpi = CPI(CPIIdentifier(), "dummy_mgm_group_id")
        val holdingId = HoldingIdentity(request.x500Name, cpi.mgmGroupId)

        val virtualNodeInfo = VirtualNodeInfo(holdingId.toAvro(), cpi.id)
        val virtualNodeRecord = Record(VIRTUAL_NODE_INFO_TOPIC, virtualNodeInfo.holdingIdentity, virtualNodeInfo)
        val future = publisher.publish(listOf(virtualNodeRecord)).first()

        try {
            future.get()
        } catch (e: Exception) {
            val errMsg = "Record $virtualNodeRecord was written to the database, but couldn't be published. Cause: $e"
            handleException(respFuture, errMsg, e, request.x500Name, cpi.id, request.cpiIdHash, cpi.mgmGroupId, holdingId, holdingId.id)
            return
        }

        val response = VirtualNodeCreationResponse(
            true, null, request.x500Name, cpi.id, request.cpiIdHash, cpi.mgmGroupId, holdingId.toAvro(), holdingId.id
        )
        respFuture.complete(response)
    }

    /** Completes the [respFuture] with an [ExceptionEnvelope]. */
    @Suppress("LongParameterList")
    private fun handleException(
        respFuture: VirtualNodeCreationResponseFuture,
        errMsg: String,
        cause: Exception,
        x500Name: String,
        cpiId: CPIIdentifier,
        cpiIdHash: String,
        mgmGroupId: String,
        holdingId: HoldingIdentity,
        holdingIdHash: String
    ): Boolean {
        val exception = ExceptionEnvelope(cause.javaClass.name, errMsg)
        val response = VirtualNodeCreationResponse(
            false, exception, x500Name, cpiId, cpiIdHash, mgmGroupId, holdingId.toAvro(), holdingIdHash
        )
        return respFuture.complete(response)
    }
}

// TODO - Joel - Describe.
private data class CPI(val id: CPIIdentifier, val mgmGroupId: String)