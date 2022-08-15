package net.corda.configuration.rpcops.impl

import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
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
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mock(), mock())
            vnodeRpcOps.start()

            verify(mockCoordinator).start()
        }

        @Test
        fun `verify coordinator is closed on stop`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mock(), mock())
            vnodeRpcOps.stop()

            verify(mockCoordinator).close()
        }

        @Test
        fun `verify coordinator isRunning defers to the coordinator`() {
            val vnodeRpcOps = VirtualNodeRPCOpsImpl(mockCoordinatorFactory, mock(), mock(), mock())
            vnodeRpcOps.isRunning

            verify(mockCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if getAllVirtualNodes is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeRPCOpsImpl(mockDownCoordinatorFactory, mock(), mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.getAllVirtualNodes()
            }

            verify(mockDownCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if createVirtualNode is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeRPCOpsImpl(mockDownCoordinatorFactory, mock(), mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.createVirtualNode(mock())
            }

            verify(mockDownCoordinator).isRunning
        }
    }
}
