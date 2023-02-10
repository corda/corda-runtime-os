package net.corda.flow.pipeline.sessions

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class CheckpointInitializerImplTest {
    private val aliceX500Name = "C=GB, L=London, O=Alice"
    private val aliceMemberName = MemberX500Name.parse(aliceX500Name)
    private val groupId = "ABC123"
    private val aliceHoldingIdentity = HoldingIdentity(aliceMemberName, groupId)

    @Test
    fun `vnode info`() {
        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
        val cpiInfoReadService = mock<CpiInfoReadService>()

        val checkpointInitializer = CheckpointInitializerImpl(virtualNodeInfoReadService, cpiInfoReadService)

        val checkpoint = mock<FlowCheckpoint>()
        val startContext = mock<FlowStartContext>()
        val waitingFor = mock<WaitingFor>()

        checkpointInitializer.initialize(checkpoint, startContext, waitingFor)

        val virtualNodeInfo = mock<VirtualNodeInfo>()
        whenever(virtualNodeInfoReadService.get(startContext.identity.toCorda())).thenReturn(virtualNodeInfo)

        //val vNodeInfo = virtualNodeInfoReadService.get(startContext.identity.toCorda())
        verify(checkpoint, times(1)).initFlowState(any(), any())
    }

    @Test
    fun `cpi metadata`() {
        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
        val cpiInfoReadService = mock<CpiInfoReadService>()

        val checkpointInitializer = CheckpointInitializerImpl(virtualNodeInfoReadService, cpiInfoReadService)

        val checkpoint = mock<FlowCheckpoint>()
        val startContext = mock<FlowStartContext>()
        val waitingFor = mock<WaitingFor>()

        checkpointInitializer.initialize(checkpoint, startContext, waitingFor)

        val virtualNodeInfo = mock<VirtualNodeInfo>()
        whenever(virtualNodeInfoReadService.get(startContext.identity.toCorda())).thenReturn(virtualNodeInfo)

        val cpiIdentifier = mock<CpiIdentifier>()
        whenever(virtualNodeInfo.cpiIdentifier).thenReturn(cpiIdentifier)
        //val cpiMetadata = mock<CpiMetadata>()
        whenever(cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier))
        //val vNodeInfo = virtualNodeInfoReadService.get(startContext.identity.toCorda())
        verify(checkpoint, times(1)).initFlowState(any(), any())
    }

    @Test
    fun `null cpi metadata`() {
        val startContext = mock<FlowStartContext>()
        whenever(startContext.identity.toCorda()).thenReturn(aliceHoldingIdentity)

/*        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
        val virtualNodeInfo = mock<VirtualNodeInfo>()  //mocks null by default
        val holdingIdentity = startContext.identity.toCorda()*/

        assertThrows<IllegalStateException> { "Failed to find the cpiMetadata for identifier $aliceHoldingIdentity" }
    }

    @Test
    fun `null vnode`() {
        val startContext = mock<FlowStartContext>()
        whenever(startContext.identity.toCorda()).thenReturn(aliceHoldingIdentity)

        /*val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
        val virtualNodeInfo = mock<VirtualNodeInfo>()  //mocks null by default
        val holdingIdentity = startContext.identity.toCorda()*/

        assertThrows<IllegalStateException> { "Failed to find the virtual node info for holder $aliceHoldingIdentity" }
    }

    @Test
    fun `waiting for set`() {
        val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
        val cpiInfoReadService = mock<CpiInfoReadService>()

        val checkpointInitializer = CheckpointInitializerImpl(virtualNodeInfoReadService, cpiInfoReadService)

        val checkpoint = mock<FlowCheckpoint>()
        val key = FlowKey(FLOW_ID_1, BOB_X500_HOLDING_IDENTITY)
        val holdingIdentity: net.corda.data.identity.HoldingIdentity = BOB_X500_HOLDING_IDENTITY

        val startContext = FlowStartContext().apply {
            statusKey = key
            identity = holdingIdentity
        }

        val waitingFor = mock<WaitingFor>()

        val virtualNodeInfo = mock<VirtualNodeInfo>()
        whenever(virtualNodeInfoReadService.get(startContext.identity.toCorda())).thenReturn(virtualNodeInfo)

        val cpiMetadata = mock<CpiMetadata>()
        val cpkMetadata = mock<CpkMetadata>()
        whenever(cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)).thenReturn(cpiMetadata)
        whenever(cpiMetadata.cpksMetadata).thenReturn(setOf(cpkMetadata))

        checkpointInitializer.initialize(checkpoint, startContext, waitingFor)

        verify(checkpoint).waitingFor = waitingFor
    }

}