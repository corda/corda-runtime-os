package net.corda.configuration.rpcops.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.httprpc.ResponseCode.INTERNAL_SERVER_ERROR
import net.corda.httprpc.ResponseCode.INVALID_INPUT_DATA
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.utilities.time.Clock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsImpl
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsInternal
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.data.packaging.CpiIdentifier as CpiIdAvro
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity as HoldingIdentityEndpointType
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo as VirtualNodeInfoEndpointType

/** Tests of [VirtualNodeRPCOpsImpl]. */
class VirtualNodeRPCOpsImplTests {
    companion object {
        private const val actor = "test_principal"

        @Suppress("Unused")
        @JvmStatic
        @BeforeAll
        fun setRPCContext() {
            val rpcAuthContext = mock<RpcAuthContext>().apply {
                whenever(principal).thenReturn(actor)
            }
            CURRENT_RPC_CONTEXT.set(rpcAuthContext)
        }
    }

    private val holdingIdentityShortHash = "holdingIdentityShortHash"
    private val cpiIdAvro = CpiIdAvro("cpiName", "1.0.0", SecureHash("SHA-256", ByteBuffer.wrap("a".toByteArray())))
    private val cpiId = CpiIdentifier(cpiIdAvro.name, cpiIdAvro.version, cpiIdAvro.signerSummaryHash.toString())
    private val holdingId = HoldingIdentity("o=test,l=test,c=GB", "mgmGroupId")
    private val vaultDdlConnectionId = null
    private val vaultDmlConnectionId = UUID.randomUUID().toString()
    private val cryptoDdlConnectionId = null
    private val cryptoDmlConnectionId = UUID.randomUUID().toString()
    private val hsmConnectionId = null
    private val clock = mock<Clock>().apply {
        whenever(instant()).thenReturn(Instant.EPOCH)
    }

    private val httpCreateVNRequest = VirtualNodeRequest(holdingId.x500Name, "hash", null, null, null, null)
    private val vnCreateSuccessfulResponse = VirtualNodeCreateResponse(
        httpCreateVNRequest.x500Name,
        cpiIdAvro,
        httpCreateVNRequest.cpiFileChecksum,
        holdingId.groupId,
        holdingId,
        holdingIdentityShortHash,
        vaultDdlConnectionId,
        vaultDmlConnectionId,
        cryptoDdlConnectionId,
        cryptoDmlConnectionId,
        hsmConnectionId,
        VirtualNodeInfo.DEFAULT_INITIAL_STATE.name
    )

    private val rpcRequestTimeoutDuration = 3000

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
    fun `createVirtualNode sends the correct request to the RPC sender`() {
        val rpcRequest = httpCreateVNRequest.run {
            VirtualNodeManagementRequest(
                clock.instant(),
                VirtualNodeCreateRequest(x500Name, cpiFileChecksum, null, null, null, null, "test_principal")
            )
        }

        val (rpcSender, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)
        vnodeRPCOps.createVirtualNode(httpCreateVNRequest)

        verify(rpcSender).sendRequest(rpcRequest)
    }

    @Test
    fun `createVirtualNode returns VirtualNodeCreationResponse if response is success`() {
        val id = holdingId.toCorda()
        val successResponse =
            VirtualNodeInfoEndpointType(
                holdingIdentity = HoldingIdentityEndpointType(id.x500Name, id.groupId, id.shortHash, id.fullHash),
                cpiIdentifier = cpiId,
                vaultDdlConnectionId = vaultDdlConnectionId,
                vaultDmlConnectionId = vaultDmlConnectionId,
                cryptoDdlConnectionId = cryptoDdlConnectionId,
                cryptoDmlConnectionId = cryptoDmlConnectionId,
                hsmConnectionId = hsmConnectionId,
                state = VirtualNodeInfo.DEFAULT_INITIAL_STATE.name,
            )

        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)

        assertEquals(successResponse, vnodeRPCOps.createVirtualNode(httpCreateVNRequest))
    }

    @Test
    fun `createVirtualNode throws HttpApiException if X500 name is invalid`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()

        val badX500 = "invalid"
        val badX500Req = VirtualNodeRequest(badX500, "hash", null, null, null, null)

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)
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
        val exception = ExceptionEnvelope("ErrorType", "errorMessage")
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps {
            VirtualNodeManagementResponse(
                clock.instant(),
                VirtualNodeManagementResponseFailure(exception)
            )
        }

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals("errorMessage", e.message)
        assertEquals(INTERNAL_SERVER_ERROR, e.responseCode)
    }

    @Test
    fun `createVirtualNode throws HttpApiException if request fails but no exception is provided`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps {
            VirtualNodeManagementResponse(
                clock.instant(),
                VirtualNodeManagementResponseFailure(null)
            )
        }

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<HttpApiException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals("Request was unsuccessful but no exception was provided.", e.message)
        assertEquals(INTERNAL_SERVER_ERROR, e.responseCode)
    }

    @Test
    fun `createVirtualNode throws if RPC sender is not set`() {
        val vnodeRPCOps = VirtualNodeRPCOpsImpl(mock(), mock())

        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)

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
        val vnCreateResponse = { throw IllegalStateException() }
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps(vnCreateResponse)

        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.start()
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)

        val e = assertThrows<VirtualNodeRPCOpsServiceException> {
            vnodeRPCOps.createVirtualNode(httpCreateVNRequest)
        }

        assertEquals("Could not complete virtual node creation request.", e.message)
    }

    @Test
    fun `getAllVirtualNodes calls VirtualNodeInfoReadService to retrieve all virtual nodes`() {
        val vnodeInfoReadService = mock<VirtualNodeInfoReadService>()
        val rpcOps = VirtualNodeRPCOpsImpl(mock(), vnodeInfoReadService)
        rpcOps.getAllVirtualNodes()
        verify(vnodeInfoReadService).getAll()
    }

    @Test
    fun `is not running if RPC sender is not created`() {
        val vnodeRPCOps = VirtualNodeRPCOpsImpl(mock(), mock())
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)
        assertFalse(vnodeRPCOps.isRunning)
    }

    @Test
    fun `is not running if RPC sender is not running`() {
        val (_, vnodeRPCOps) = getVirtualNodeRPCOps()
        vnodeRPCOps.createAndStartRpcSender(mock())
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)
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
        vnodeRPCOps.setTimeout(rpcRequestTimeoutDuration)
        assertTrue(vnodeRPCOps.isRunning)
    }

    /** Returns a [VirtualNodeRPCOpsInternal] where the RPC sender returns [future] in response to any RPC requests. */
    private fun getVirtualNodeRPCOps(
        vnManagementResponse: () -> VirtualNodeManagementResponse = {
            VirtualNodeManagementResponse(clock.instant(), vnCreateSuccessfulResponse)
        }
    ): Pair<RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>, VirtualNodeRPCOpsInternal> {
        val vnCreateResponseFuture = CompletableFuture.supplyAsync(vnManagementResponse)
        val rpcSender = mock<RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>().apply {
            whenever(sendRequest(any())).thenReturn(vnCreateResponseFuture)
        }
        val publisherFactory = mock<PublisherFactory>().apply {
            whenever(createRPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>(any(), any()))
                .thenReturn(rpcSender)
        }
        return rpcSender to VirtualNodeRPCOpsImpl(publisherFactory, mock(), clock)
    }
}
