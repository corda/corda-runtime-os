package net.corda.flow.pipeline.sessions

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.FLOW_ID_1
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class CheckpointInitializerImplTest {

    @Test
    fun `Initializes the checkpoint and saves cpkFilesHashes to the checkpoint`() {
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

        checkpointInitializer.initialize(checkpoint, waitingFor, holdingIdentity.toCorda()) {
            startContext
        }
        verify(checkpoint).initFlowState(any(), any())
        assertThat(checkpoint.cpkFileHashes).hasSameClassAs(java.util.HashSet<SecureHash>())
    }

    @Test
    fun `Null cpi metadata throws FlowTransientException`() {
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

        val nullCpiMetadata = null
        whenever(cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)).thenReturn(nullCpiMetadata)

        assertThrows<FlowTransientException> {
            checkpointInitializer.initialize(
                checkpoint,
                waitingFor,
                holdingIdentity.toCorda()
            ) {
                startContext
            }
        }
    }

    @Test
    fun `Null virtualNodeInfo throws FlowTransientException`() {
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
        val nullVirtualNodeInfo = null
        whenever(virtualNodeInfoReadService.get(startContext.identity.toCorda())).thenReturn(nullVirtualNodeInfo)

        assertThrows<FlowTransientException> {
            checkpointInitializer.initialize(
                checkpoint,
                waitingFor,
                holdingIdentity.toCorda()
            ) {
                startContext
            }
        }
    }

    @Test
    fun `WaitingFor value is set`() {
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

        checkpointInitializer.initialize(checkpoint, waitingFor, holdingIdentity.toCorda()){
            startContext
        }

        verify(checkpoint).waitingFor = waitingFor
    }

}