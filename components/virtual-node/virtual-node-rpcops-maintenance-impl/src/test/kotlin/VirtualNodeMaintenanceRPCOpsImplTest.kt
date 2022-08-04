import net.corda.chunking.ChunkWriter
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.virtualnode.maintenance.rpcops.impl.v1.UnknownMaintenanceResponseTypeException
import net.corda.libs.virtualnode.maintenance.rpcops.impl.v1.VirtualNodeMaintenanceRPCOpsImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream

/** Tests of [VirtualNodeRPCOpsImpl]. */
class VirtualNodeMaintenanceRPCOpsImplTest {
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
        whenever(status) doReturn LifecycleStatus.DOWN
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
            val vnodeMaintenanceRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mock(), mock())
            vnodeMaintenanceRpcOps.start()

            verify(mockCoordinator).start()
        }

        @Test
        fun `verify coordinator is closed on stop`() {
            val vnodeMaintenanceRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mock(), mock())
            vnodeMaintenanceRpcOps.stop()

            verify(mockCoordinator).close()
        }

        @Test
        fun `verify coordinator isRunning defers to the coordinator`() {
            val vnodeMaintenanceRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mock(), mock())
            vnodeMaintenanceRpcOps.isRunning

            verify(mockCoordinator).isRunning
            assertTrue(vnodeMaintenanceRpcOps.isRunning)
        }

        @Test
        fun `verify exception throw if forceCpiUpload is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockDownCoordinatorFactory, mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.forceCpiUpload(mock())
            }

            verify(mockDownCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if updateVirtualNodeState is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockDownCoordinatorFactory, mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.updateVirtualNodeState("someId", "someState")
            }

            verify(mockDownCoordinator).isRunning
        }
    }

    @Nested
    inner class ServiceAPITests {
        // Fake response to trigger else branch
        inner class DummyResponse

        // Mocks
        private val mockVirtualNodeUpdateResponse = mock<VirtualNodeStateChangeResponse>().apply {
            whenever(holdingIdentityShortHash) doReturn "someID"
            whenever(virtualNodeState) doReturn "someState"
        }
        private val mockVirtualNodeFailedResponse = mock<VirtualNodeManagementResponseFailure>()
        private val mockFailedVirtualNodeManagementResponse = mock<VirtualNodeManagementResponse>().apply {
            whenever(responseType) doReturn mockVirtualNodeFailedResponse
        }
        private val mockBadVirtualNodeManagementResponse = mock<VirtualNodeManagementResponse>().apply {
            whenever(responseType) doReturn DummyResponse()
        }
        val mockUpload = mock<HttpFileUpload>().apply {
            whenever(fileName) doReturn "test"
            whenever(content) doReturn InputStream.nullInputStream()
        }
        private val mockCpiResponse = mock<ChunkWriter.Request>().apply {
            whenever(requestId) doReturn "something"
        }
        private val mockCpiUploadManager = mock<CpiUploadManager>().apply {
            whenever(uploadCpi(any(), any(), any())) doReturn mockCpiResponse
        }
        private val mockCpiUploadRPCOpsService = mock<CpiUploadRPCOpsService>().apply {
            whenever(cpiUploadManager) doReturn mockCpiUploadManager
        }
        private val mockUpdateResponse = mock<VirtualNodeManagementResponse>().apply {
            whenever(responseType) doReturn mockVirtualNodeUpdateResponse
        }
        private val mockVirtualNodeSenderService = mock<VirtualNodeSenderService>().apply {
            whenever(sendAndReceive(any())) doReturn mockUpdateResponse
        }
        private val mockFailVirtualNodeSenderService = mock<VirtualNodeSenderService>().apply {
            whenever(sendAndReceive(any())) doReturn mockFailedVirtualNodeManagementResponse
        }
        private val mockBadVirtualNodeSenderService = mock<VirtualNodeSenderService>().apply {
            whenever(sendAndReceive(any())) doReturn mockBadVirtualNodeManagementResponse
        }

        @Test
        fun `verify forceCpiUpload performs call to uploadCpi on cpiUploadManager`() {
            val vnodeRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mockCpiUploadRPCOpsService, mock())
            vnodeRpcOps.forceCpiUpload(mockUpload)

            verify(mockCpiUploadRPCOpsService.cpiUploadManager)
                .uploadCpi(
                    any(),
                    any(),
                    any()
                )
        }

        @Test
        fun `verify updateVirtualNodeState performs call to sendAndReceive on sender service`() {
            val vnodeRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mock(), mockVirtualNodeSenderService)
            vnodeRpcOps.updateVirtualNodeState("someId", "someNewState")

            verify(mockVirtualNodeSenderService).sendAndReceive(any())
        }

        @Test
        fun `verify createVirtualNode throws an exception on a failure response`() {
            val vnodeRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mock(), mockFailVirtualNodeSenderService)

            assertThrows<InternalServerException> {
                vnodeRpcOps.updateVirtualNodeState("someId", "someNewState")
            }
        }

        @Test
        fun `verify createVirtualNode throws an exception on an unknown response`() {
            val vnodeRpcOps = VirtualNodeMaintenanceRPCOpsImpl(mockCoordinatorFactory, mock(), mockBadVirtualNodeSenderService)

            assertThrows<UnknownMaintenanceResponseTypeException> {
                vnodeRpcOps.updateVirtualNodeState("someId", "someNewState")
            }
        }
    }
}
