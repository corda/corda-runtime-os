package net.corda.configuration.rpcops.impl.tests

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.httprpc.ResponseCode.INTERNAL_SERVER_ERROR
import net.corda.httprpc.ResponseCode.INVALID_INPUT_DATA
import net.corda.httprpc.exception.HttpApiException
import net.corda.libs.virtualnode.endpoints.v1.types.CPIIdentifier
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsImpl
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import net.corda.data.packaging.CPIIdentifier as CPIIdAvro

/** Tests of [VirtualNodeRPCOpsImpl]. */
class VirtualNodeRPCOpsImplTests {
    private val holdingIdHash = "holdingIdHash"
    private val cpiIdAvro = CPIIdAvro("cpiName", "1.0.0", SecureHash("SHA-256", ByteBuffer.wrap("a".toByteArray())))
    private val cpiId = CPIIdentifier(cpiIdAvro.name, cpiIdAvro.version, cpiIdAvro.signerSummaryHash.toString())
    private val holdingId = HoldingIdentity("o=test,l=test,c=GB", "mgmGroupId")

    private val req = HTTPCreateVirtualNodeRequest(holdingId.x500Name, "hash")
    private val successFuture = CompletableFuture.supplyAsync {
        VirtualNodeCreationResponse(
            true, null, req.x500Name, cpiIdAvro, req.cpiIdHash, holdingId.groupId, holdingId, holdingIdHash
        )
    }
    private val successResponse =
        HTTPCreateVirtualNodeResponse(holdingId.x500Name, cpiId, req.cpiIdHash, holdingId.groupId, holdingIdHash)

    @Test
    fun `createAndStartRPCSender starts new RPC sender`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRPCSender(mock())

        verify(rpcSender).start()
    }

    @Test
    fun `createAndStartRPCSender closes existing RPC sender if one exists`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.createAndStartRPCSender(mock())

        verify(rpcSender).close()
    }

    @Test
    fun `stop closes existing RPC sender if one exists`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.stop()

        verify(rpcSender).close()
    }

    @Test
    fun `createVirtualNode sends the correct request to the RPC sender`() {
        val rpcRequest = req.run { VirtualNodeCreationRequest(x500Name, cpiIdHash) }

        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(1000)
        vnodeRPCOps.createVirtualNode(req)

        verify(rpcSender).sendRequest(rpcRequest)
    }

    @Test
    fun `createVirtualNode returns VirtualNodeCreationResponse if response is success`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(1000)

        assertEquals(successResponse, vnodeRPCOps.createVirtualNode(req))
    }

    @Test
    fun `createVirtualNode throws HttpApiException if X500 name is invalid`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()

        val badX500 = "invalid"
        val badX500Req = HTTPCreateVirtualNodeRequest(badX500, "hash")

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(badX500Req)
        }

        assertEquals(
            "X500 name \"$badX500\" could not be parsed. Cause: improperly specified input name: $badX500",
            e.message
        )
        assertEquals(INVALID_INPUT_DATA, e.responseCode)
    }

    @Test
    fun `createVirtualNode throws HttpApiException if response is failure`() {
        val exception = ExceptionEnvelope("ErrorType", "ErrorMessage.")
        val response = req.run {
            VirtualNodeCreationResponse(false, exception, "", mock(), "", "", mock(), "")
        }
        val future = CompletableFuture.supplyAsync { response }

        val (_, vnodeRPCOps) = getVirtualNodeRPCOps(future)

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(req)
        }

        assertEquals("ErrorType: ErrorMessage.", e.message)
        assertEquals(INTERNAL_SERVER_ERROR, e.responseCode)
    }

    @Test
    fun `createVirtualNode throws HttpApiException if request fails but no exception is provided`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps(CompletableFuture.supplyAsync {
            VirtualNodeCreationResponse(false, null, "", mock(), "", "", mock(), "")
        })

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(req)
        }

        assertEquals("Request was unsuccessful but no exception was provided.", e.message)
        assertEquals(INTERNAL_SERVER_ERROR, e.responseCode)
    }

    @Test
    fun `createVirtualNode throws if RPC sender is not set`() {
        val vnodeRPCOps = VirtualNodeRPCOpsImpl(mock())

        vnodeRPCOps.setTimeout(1000)
        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(req)
        }

        assertEquals(
            "Configuration update request could not be sent as no RPC sender has been created.",
            e.message
        )
    }

    @Test
    fun `createVirtualNode throws if request timeout is not set`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRPCSender(mock())
        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(req)
        }

        assertEquals(
            "Configuration update request could not be sent as the request timeout has not been set.",
            e.message
        )
    }

    @Test
    fun `createVirtualNode throws VirtualNodeRPCOpsServiceException if response future completes exceptionally`() {
        val future = CompletableFuture.supplyAsync<VirtualNodeCreationResponse> { throw IllegalStateException() }
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps(future)

        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(1000)
        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(req)
        }

        assertEquals("Could not create virtual node.", e.message)
    }

    @Test
    fun `is not running if RPC sender is not created`() {
        val vnodeRPCOps = VirtualNodeRPCOpsImpl(mock())
        vnodeRPCOps.setTimeout(0)
        assertFalse(vnodeRPCOps.isRunning)
    }

    @Test
    fun `is not running if RPC timeout is not set`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()
        vnodeRPCOps.createAndStartRPCSender(mock())
        assertFalse(vnodeRPCOps.isRunning)
    }

    @Test
    fun `is running if RPC sender is created and RPC timeout is set`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()
        vnodeRPCOps.createAndStartRPCSender(mock())
        vnodeRPCOps.setTimeout(0)
        assertTrue(vnodeRPCOps.isRunning)
    }

    /** Returns a [VirtualNodeRPCOpsInternal] where the RPC sender returns [future] in response to any RPC requests. */
    private fun getVirtualNodeRPCOps(
        future: CompletableFuture<VirtualNodeCreationResponse> = successFuture
    ): Pair<RPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>, VirtualNodeRPCOpsInternal> {

        val rpcSender = mock<RPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>>().apply {
            whenever(sendRequest(any())).thenReturn(future)
        }
        val publisherFactory = mock<PublisherFactory>().apply {
            whenever(createRPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>(any(), any()))
                .thenReturn(rpcSender)
        }
        return rpcSender to VirtualNodeRPCOpsImpl(publisherFactory)
    }
}