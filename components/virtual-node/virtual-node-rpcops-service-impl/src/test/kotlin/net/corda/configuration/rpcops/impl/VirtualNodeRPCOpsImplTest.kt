package net.corda.configuration.rpcops.impl

import net.corda.data.crypto.SecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderService
import net.corda.virtualnode.rpcops.impl.v1.UnknownResponseTypeException
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsImpl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID
import net.corda.data.packaging.CpiIdentifier as CpiIdAvro

/** Tests of [VirtualNodeRPCOpsImpl]. */
class VirtualNodeRPCOpsImplTest {
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

    private val mockCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning) doReturn true
    }
    private val mockCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        whenever(createCoordinator(any(), any())) doReturn mockCoordinator
    }

    @Nested
    inner class LifecycleTests {
        private val mockDownCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(isRunning) doReturn false
        }
        private val mockDownCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn mockDownCoordinator
        }

        @Test
        fun `verify coordinator is started on start`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mock())
            vnodeRpcOps.start()

            verify(mockCoordinator).start()
        }

        @Test
        fun `verify coordinator is closed on stop`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mock())
            vnodeRpcOps.stop()

            verify(mockCoordinator).close()
        }

        @Test
        fun `verify coordinator isRunning defers to the coordinator`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mock())
            vnodeRpcOps.isRunning

            verify(mockCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if getAllVirtualNodes is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeRPCOpsImpl(mockDownCoordinatorFactory, mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.getAllVirtualNodes()
            }

            verify(mockDownCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if createVirtualNode is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeRPCOpsImpl(mockDownCoordinatorFactory, mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.createVirtualNode(mock())
            }

            verify(mockDownCoordinator).isRunning
        }
    }

    @Nested
    inner class ServiceAPITests {
        // Fake response to trigger else branch
        inner class DummyResponse

        // Real data classes
        private val cpiIdAvro = CpiIdAvro("cpiName", "1.0.0", SecureHash("SHA-256", ByteBuffer.wrap("a".toByteArray())))
        private val holdingId = HoldingIdentity("o=test,l=test,c=GB", "mgmGroupId")

        // Mocks
        private val mockVirtualNodeCreateResponse = mock<VirtualNodeCreateResponse>().apply {
            whenever(x500Name) doReturn holdingId.x500Name
            whenever(cpiIdentifier) doReturn cpiIdAvro
            whenever(mgmGroupId) doReturn holdingId.groupId
            whenever(holdingIdentifierHash) doReturn "holdingIdentifierHash"
            whenever(vaultDdlConnectionId) doReturn null
            whenever(vaultDmlConnectionId) doReturn UUID.randomUUID().toString()
            whenever(cryptoDdlConnectionId) doReturn null
            whenever(cryptoDmlConnectionId) doReturn UUID.randomUUID().toString()
            whenever(virtualNodeState) doReturn "virtualNodeState"
        }
        private val mockVirtualNodeFailedResponse = mock<VirtualNodeManagementResponseFailure>()
        private val mockVirtualNodeBadResponse = mock<DummyResponse>()
        private val mockFailedVirtualNodeManagementResponse = mock<VirtualNodeManagementResponse>().apply {
            whenever(responseType) doReturn mockVirtualNodeFailedResponse
        }
        private val mockBadVirtualNodeManagementResponse = mock<VirtualNodeManagementResponse>().apply {
            whenever(responseType) doReturn mockVirtualNodeBadResponse
        }

        private val mockVirtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
        private val mockCreateResponse = mock<VirtualNodeManagementResponse>().apply {
            whenever(responseType) doReturn mockVirtualNodeCreateResponse
        }
        private val mockVirtualNodeSenderService = mock<VirtualNodeSenderService>().apply {
            whenever(sendAndReceive(any())) doReturn mockCreateResponse
        }
        private val mockFailVirtualNodeSenderService = mock<VirtualNodeSenderService>().apply {
            whenever(sendAndReceive(any())) doReturn mockFailedVirtualNodeManagementResponse
        }
        private val mockBadVirtualNodeSenderService = mock<VirtualNodeSenderService>().apply {
            whenever(sendAndReceive(any())) doReturn mockBadVirtualNodeManagementResponse
        }

        @Test
        fun `verify getAllVirtualNodes performs call to getAll on read service`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mockVirtualNodeInfoReadService, mock())
            vnodeRpcOps.getAllVirtualNodes()

            verify(mockVirtualNodeInfoReadService).getAll()
        }

        @Test
        fun `verify createVirtualNode performs call to sendAndReceive on read service`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mockVirtualNodeSenderService)
            val mockRequest = mock<VirtualNodeRequest>().apply {
                whenever(x500Name) doReturn "o=test,l=test,c=GB"
            }
            vnodeRpcOps.createVirtualNode(mockRequest)

            verify(mockVirtualNodeSenderService).sendAndReceive(any())
        }

        @Test
        fun `verify createVirtualNode throws an exception on a failure response`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mockFailVirtualNodeSenderService)
            val mockRequest = mock<VirtualNodeRequest>().apply {
                whenever(x500Name) doReturn "o=test,l=test,c=GB"
            }

            assertThrows<InternalServerException> {
                vnodeRpcOps.createVirtualNode(mockRequest)
            }
        }

        @Test
        fun `verify createVirtualNode throws an exception on an unknown response`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mockBadVirtualNodeSenderService)
            val mockRequest = mock<VirtualNodeRequest>().apply {
                whenever(x500Name) doReturn "o=test,l=test,c=GB"
            }

            assertThrows<UnknownResponseTypeException> {
                vnodeRpcOps.createVirtualNode(mockRequest)
            }
        }
    }
}
