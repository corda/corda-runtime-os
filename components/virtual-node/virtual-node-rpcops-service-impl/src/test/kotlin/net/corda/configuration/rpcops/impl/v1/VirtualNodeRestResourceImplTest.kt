package net.corda.configuration.rpcops.impl.v1

import java.util.UUID
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.InvalidStateChangeException
import net.corda.httprpc.security.CURRENT_REST_CONTEXT
import net.corda.httprpc.security.RestAuthContext
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.utilities.time.ClockFactory
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRestResourceImpl
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [VirtualNodeRestResourceImpl]. */
class VirtualNodeRestResourceImplTest {
    companion object {
        private const val actor = "test_principal"

        @Suppress("Unused")
        @JvmStatic
        @BeforeAll
        fun setRPCContext() {
            val restAuthContext = mock<RestAuthContext>().apply {
                whenever(principal).thenReturn(actor)
            }
            CURRENT_REST_CONTEXT.set(restAuthContext)
        }
    }

    private val mockCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning) doReturn true
    }
    private val mockCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        whenever(createCoordinator(any(), any())) doReturn mockCoordinator
    }

    private val mockClockFactory = mock<ClockFactory>().apply {
        whenever(createUTCClock()) doReturn UTCClock()
    }

    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>().apply {
        whenever(getByHoldingIdentityShortHash(any())) doReturn mockVnode()
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
            val vnodeResource =
                VirtualNodeRestResourceImpl(mockCoordinatorFactory, mock(), mock(), mock(), mock(), mockClockFactory)
            vnodeResource.start()

            verify(mockCoordinator).start()
        }

        @Test
        fun `verify coordinator is stopped on stop`() {
            val vnodeResource =
                VirtualNodeRestResourceImpl(mockCoordinatorFactory, mock(), mock(), mock(), mock(), mockClockFactory)
            vnodeResource.stop()

            verify(mockCoordinator).stop()
        }

        @Test
        fun `verify coordinator isRunning defers to the coordinator`() {
            val vnodeResource =
                VirtualNodeRestResourceImpl(mockCoordinatorFactory, mock(), mock(), mock(), mock(), mockClockFactory)
            vnodeResource.isRunning

            verify(mockCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if getAllVirtualNodes is performed while coordinator is not running`() {
            val vnodeMaintenanceResource = VirtualNodeRestResourceImpl(
                mockDownCoordinatorFactory,
                mock(),
                mock(),
                mock(),
                mock(),
                mockClockFactory
            )
            assertThrows<IllegalStateException> {
                vnodeMaintenanceResource.getAllVirtualNodes()
            }

            verify(mockDownCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if createVirtualNode is performed while coordinator is not running`() {
            val vnodeMaintenanceResource = VirtualNodeRestResourceImpl(
                mockDownCoordinatorFactory,
                mock(),
                mock(),
                mock(),
                mock(),
                mockClockFactory
            )
            assertThrows<IllegalStateException> {
                vnodeMaintenanceResource.createVirtualNode(mock())
            }

            verify(mockDownCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if updateVirtualNodeState is performed while coordinator is not running`() {
            val vnodeMaintenanceResource =
                VirtualNodeRestResourceImpl(
                    mockDownCoordinatorFactory,
                    mock(),
                    mock(),
                    mock(),
                    mock(),
                    mockClockFactory
                )
            assertThrows<IllegalStateException> {
                vnodeMaintenanceResource.updateVirtualNodeState("someId", "someState")
            }

            verify(mockDownCoordinator).isRunning
        }
    }

    @Test
    fun `cant update virtual node state to same state`() {
        val vnodeResource =
            VirtualNodeRestResourceImpl(mockCoordinatorFactory, mock(), virtualNodeInfoReadService, mock(), mock(), mockClockFactory)
        vnodeResource.start()

        assertThrows<InvalidStateChangeException> {
            vnodeResource.updateVirtualNodeState("ABCABC123123", "ACTIVE")
        }
    }

    @Test
    fun `cant set state of virtual node to non defined value`() {
        val vnodeResource =
            VirtualNodeRestResourceImpl(mockCoordinatorFactory, mock(), virtualNodeInfoReadService, mock(), mock(), mockClockFactory)
        vnodeResource.start()

        assertThrows<InvalidInputDataException> {
            vnodeResource.updateVirtualNodeState("ABCABC123123", "BLAHBLAHBLAH")
        }
    }

    private fun mockVnode(operational: OperationalStatus = OperationalStatus.ACTIVE): VirtualNodeInfo? {
        return VirtualNodeInfo(
            HoldingIdentity(MemberX500Name("test","IE","IE"), "group"),
            CpiIdentifier("cpi","1", SecureHash.parse("SHA-256:1234567890")),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            operational,
            operational,
            operational,
            operational,
            null,
            -1,
            mock(),
            false
        )
    }
}
