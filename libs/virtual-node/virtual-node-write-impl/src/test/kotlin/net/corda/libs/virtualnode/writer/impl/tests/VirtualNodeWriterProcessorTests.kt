package net.corda.libs.virtualnode.writer.impl.tests

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.libs.virtualnode.write.VirtualNodeWriterException
import net.corda.libs.virtualnode.write.impl.EntityRepository
import net.corda.libs.virtualnode.write.impl.VirtualNodeWriterProcessor
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** Tests of [VirtualNodeWriterProcessor]. */
class VirtualNodeWriterProcessorTests {
    private val holdingIdentity = HoldingIdentity("x500Name", "dummy_mgm_group_id")
    private val vnodeInfo = let {
        val secureHash = SecureHash(
            "SHA-256",
            ByteBuffer.wrap("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray())
        )
        VirtualNodeInfo(
            holdingIdentity.toAvro(),
            CPIIdentifier("dummy_name", "dummy_version", secureHash)
        )
    }
    private val vnodeCreationReq =
        VirtualNodeCreationRequest(vnodeInfo.holdingIdentity.x500Name, "dummy_cpi_id_short_hash")

    private val publisherError = CordaMessageAPIIntermittentException("Error.")

    /** Returns a mock [Publisher]. */
    private fun getPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    /** Returns a mock [Publisher] that throws an error whenever it tries to publish. */
    private fun getErroringPublisher() = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(
            listOf(CompletableFuture.supplyAsync { throw publisherError })
        )
    }

    /** Calls [processor].`onNext` for the given [req], and returns the result of the future. */
    private fun processRequest(
        processor: VirtualNodeWriterProcessor,
        req: VirtualNodeCreationRequest
    ): VirtualNodeCreationResponse {

        val respFuture = CompletableFuture<VirtualNodeCreationResponse>()
        processor.onNext(req, respFuture)
        return respFuture.get()
    }

    @Test
    fun `publishes correct virtual node info to Kafka`() {
        val expectedRecord = Record(VIRTUAL_NODE_INFO_TOPIC, vnodeInfo.holdingIdentity, vnodeInfo)

        val publisher = getPublisher()
        val processor = VirtualNodeWriterProcessor(publisher, EntityRepository())
        processRequest(processor, vnodeCreationReq)

        verify(publisher).publish(listOf(expectedRecord))
    }

    @Test
    fun `sends RPC success response after publishing virtual node info to Kafka`() {
        val expectedResp = VirtualNodeCreationResponse(
            true,
            null,
            vnodeCreationReq.x500Name,
            vnodeInfo.cpiIdentifier,
            vnodeCreationReq.cpiIdHash,
            vnodeInfo.holdingIdentity.groupId,
            vnodeInfo.holdingIdentity,
            holdingIdentity.id
        )

        val processor = VirtualNodeWriterProcessor(getPublisher(), EntityRepository())
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if fails to publish virtual node info to Kafka`() {
        val expectedRecord = Record(VIRTUAL_NODE_INFO_TOPIC, vnodeInfo.holdingIdentity, vnodeInfo)
        val expectedEnvelope = ExceptionEnvelope(
            ExecutionException::class.java.name,
            "Record $expectedRecord was written to the database, but couldn't be published. Cause: " +
                    "${ExecutionException(publisherError)}"
        )
        val expectedResp = VirtualNodeCreationResponse(
            false,
            expectedEnvelope,
            vnodeCreationReq.x500Name,
            vnodeInfo.cpiIdentifier,
            vnodeCreationReq.cpiIdHash,
            vnodeInfo.holdingIdentity.groupId,
            vnodeInfo.holdingIdentity,
            holdingIdentity.id
        )

        val processor = VirtualNodeWriterProcessor(getErroringPublisher(), EntityRepository())
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp, resp)
    }

    // TODO - Joel - Test when there's a holding ID collision.

    @Test
    fun `sends RPC failure response if the CPI with the given ID is not stored on the node`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriterException::class.java.name,
            "CPI with hash ${vnodeCreationReq.cpiIdHash} was not found."
        )
        val expectedResp = VirtualNodeCreationResponse(false, expectedEnvelope, null, null, null, null, null, null)

        val EntityRepository = mock<EntityRepository>().apply {
            whenever(getCPIMetadata(any())).thenReturn(null)
        }
        val processor = VirtualNodeWriterProcessor(getPublisher(), EntityRepository)
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp, resp)
    }

    @Test
    fun `sends RPC failure response if the there is a holding-identity collision`() {
        val expectedEnvelope = ExceptionEnvelope(
            VirtualNodeWriterException::class.java.name,
            "CPI with hash ${vnodeCreationReq.cpiIdHash} was not found."
        )
        val expectedResp = VirtualNodeCreationResponse(false, expectedEnvelope, null, null, null, null, null, null)

        val EntityRepository = mock<EntityRepository>().apply {
            whenever(getHoldingIdentity(any())).thenReturn(mock())
        }
        val processor = VirtualNodeWriterProcessor(getPublisher(), EntityRepository)
        val resp = processRequest(processor, vnodeCreationReq)

        assertEquals(expectedResp, resp)
    }
}