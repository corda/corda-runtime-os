package net.corda.configuration.rpcops.impl.v1

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.rest.security.RestContextProvider
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.rest.exception.InvalidInputDataException
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import net.corda.virtualnode.rpcops.factories.RequestFactory
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRestResourceImpl
import net.corda.virtualnode.rpcops.impl.validation.VirtualNodeValidationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class VirtualNodeRestResourceImplTest {

    private val virtualNodeSender = mock<VirtualNodeSender>()
    private val virtualNodeValidationService = mock<VirtualNodeValidationService>()
    private val requestFactory = mock<RequestFactory>()
    private val restContextProvider = mock<RestContextProvider>().apply {
        whenever(principal).thenReturn("user1")
    }

    private val mockCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning).thenReturn(true)
        whenever(this.getManagedResource<VirtualNodeSender>("SENDER")).thenReturn(virtualNodeSender)
    }
    private val mockCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        whenever(createCoordinator(any(), any())) doReturn mockCoordinator
    }
    private val mockDownCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning) doReturn false
    }

    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>().apply {
        whenever(getByHoldingIdentityShortHash(any())) doReturn mockVnode()
    }

    @Test
    fun `verify coordinator is started on start`() {
        val vnodeRpcOps = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        vnodeRpcOps.start()

        verify(mockCoordinator).start()
    }

    @Test
    fun `verify coordinator is stopped on stop`() {
        val vnodeRpcOps = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        vnodeRpcOps.stop()

        verify(mockCoordinator).stop()
    }

    @Test
    fun `verify coordinator isRunning defers to the coordinator`() {
        val vnodeRpcOps = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        vnodeRpcOps.isRunning

        verify(mockCoordinator).isRunning
    }

    @Test
    fun `create virtual node`() {
        val groupId = "grp1"
        val requestId = "r1"
        val request = CreateVirtualNodeRequest(
            "",
            "",
            null,
            null,
            null,
            null,
            null,
            null
        )
        val holdingIdentity = mock<HoldingIdentity>()
        val asyncRequest = VirtualNodeAsynchronousRequest().apply { this.requestId = requestId }

        whenever(virtualNodeValidationService.validateAndGetGroupId(request)).thenReturn(groupId)
        whenever(requestFactory.createHoldingIdentity(groupId, request)).thenReturn(holdingIdentity)
        whenever(requestFactory.createVirtualNodeRequest(holdingIdentity, request)).thenReturn(asyncRequest)

        val target = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)

        val result = target.createVirtualNode(request)

        verify(virtualNodeValidationService).validateVirtualNodeDoesNotExist(holdingIdentity)
        verify(virtualNodeSender).sendAsync(requestId, asyncRequest)
        assertThat(result.responseBody.requestId).isEqualTo(requestId)
    }

    private fun createVirtualNodeRestResourceImpl(lifecycleCoordinatorFactory: LifecycleCoordinatorFactory): VirtualNodeRestResourceImpl {
        return VirtualNodeRestResourceImpl(
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock(),
            mock(),
            requestFactory,
            UTCClock(),
            virtualNodeValidationService,
            restContextProvider
        )
    }

    @Test
    fun `cant set state of virtual node to non defined value`() {
        val vnodeResource =createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
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
