package net.corda.configuration.rpcops.impl.tests

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.httprpc.exception.HttpApiException
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.internal.VirtualNodeRPCOpsInternal
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsImpl
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
import java.time.Duration
import java.util.concurrent.CompletableFuture

// TODO - Expand these tests to cover success responses once these contain meaningful data.
/** Tests of [VirtualNodeRPCOpsImpl]. */
class VirtualNodeRPCOpsImplTests {
    private val cpiId = CPIIdentifier("cpiName", "1.0.0", SecureHash("SHA-256", ByteBuffer.wrap("a".toByteArray())))
    private val holdingId = HoldingIdentity("holdingIdName", "groupId")

    private val httpCreateVNRequest = HTTPCreateVirtualNodeRequest("name", "hash")
    private val vnCreateSuccessfulResponse = VirtualNodeCreationResponse(
        true,
        null,
        httpCreateVNRequest.x500Name,
        cpiId,
        httpCreateVNRequest.cpiIdHash,
        "mgmGroupId",
        holdingId,
        "holdingIdHash"
    )
    private val rpcRequestTimeoutDuration = Duration.ofMillis(1000.toLong())

    @Test
    fun `createAndStartRPCSender starts new RPC sender`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRpcSender(mock())

        verify(rpcSender).start()
    }

    @Test
    fun `createAndStartRPCSender closes existing RPC sender if one exists`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.createAndStartRpcSender(mock())

        verify(rpcSender).close()
    }

    @Test
    fun `stop closes existing RPC sender if one exists`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.stop()

        verify(rpcSender).close()
    }

    @Test
    fun `createVirtualNode throws HttpApiException if response is failure`() {
        val exception = ExceptionEnvelope("ErrorType", "errorMessage")
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps{
            VirtualNodeCreationResponse(false, exception, "", mock(), "", "", mock(), "")
        }

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals("ErrorType: errorMessage", e.message)
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `createVirtualNode throws HttpApiException if request fails but no exception is provided`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps {
            VirtualNodeCreationResponse(false, null, "", mock(), "", "", mock(), "")
        }

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals("Request was unsuccessful but no exception was provided.", e.message)
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `createVirtualNode throws if RPC sender is not set`() {
        val vnodeRPCOps = VirtualNodeRPCOpsImpl(mock())

        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals(
            "Configuration update request could not be sent as no RPC sender has been created.",
            e.message
        )
    }

    @Test
    fun `createVirtualNode throws if request timeout is not set`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRpcSender(mock())

        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals(
            "Configuration update request could not be sent as the request timeout has not been set.",
            e.message
        )
    }

    @Test
    fun `createVirtualNode throws VirtualNodeRPCOpsServiceException if response future completes exceptionally`() {
        val vnCreateResponse =  { throw IllegalStateException() }
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps(vnCreateResponse)

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals("Could not create virtual node.", e.message)
    }

    @Test
    fun `is not running if RPC sender is not created`() {
        val vnodeRPCOps = VirtualNodeRPCOpsImpl(mock())
        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)
        assertFalse(vnodeRPCOps.isRunning)
    }

    @Test
    fun `is not running if RPC sender is not running`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()
        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)
        assertFalse(vnodeRPCOps.isRunning)
    }

    @Test
    fun `is not running if RPC timeout is not set`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()
        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        assertFalse(vnodeRPCOps.isRunning)
    }

    @Test
    fun `is running if RPC sender is created and is running and RPC timeout is set`() {
        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()
        whenever(rpcSender.isRunning).thenReturn(true)

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setRpcRequestTimeout(rpcRequestTimeoutDuration)
        assertTrue(vnodeRPCOps.isRunning)
    }

    /** Returns a [VirtualNodeRPCOpsInternal] where the RPC sender returns [future] in response to any RPC requests. */
    private fun getVirtualNodeRPCOps(
        vnCreateResponse: () -> VirtualNodeCreationResponse = { vnCreateSuccessfulResponse }
    ): Pair<RPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>, VirtualNodeRPCOpsInternal> {

        val vnCreateResponseFuture = CompletableFuture.supplyAsync(vnCreateResponse)
        val rpcSender = mock<RPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>>().apply {
            whenever(sendRequest(any())).thenReturn(vnCreateResponseFuture)
        }
        val publisherFactory = mock<PublisherFactory>().apply {
            whenever(createRPCSender<VirtualNodeCreationRequest, VirtualNodeCreationResponse>(any(), any()))
                .thenReturn(rpcSender)
        }
        return rpcSender to VirtualNodeRPCOpsImpl(publisherFactory)
    }
}