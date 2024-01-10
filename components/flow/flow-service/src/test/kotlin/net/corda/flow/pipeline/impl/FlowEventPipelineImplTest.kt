package net.corda.flow.pipeline.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.external.ExternalEventResponse
import net.corda.flow.BOB_X500
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import net.corda.data.flow.state.waiting.Wakeup as WakeUpWaitingFor

class FlowEventPipelineImplTest {
    private val runOrContinueTimeout = 60000L
    private val payload = ExternalEventResponse("foo")
    private val waitingForWakeup = WaitingFor(WakeUpWaitingFor())
    private val mockHoldingIdentity = mock<HoldingIdentity>().apply {
        whenever(shortHash).thenReturn(ShortHash.Companion.of("0123456789abc"))
    }
    private val mockFlowId = "flow_id_111"
    private val checkpoint = mock<FlowCheckpoint>().apply {
        whenever(waitingFor).thenReturn(waitingForWakeup)
        whenever(holdingIdentity).thenReturn(mockHoldingIdentity)
        whenever(flowId).thenReturn(mockFlowId)
        whenever(flowStartContext).thenReturn(FlowStartContext().apply {
            this.flowClassName = "f1"
            this.requestId = "r1"
            this.initiatedBy = net.corda.data.identity.HoldingIdentity(BOB_X500, "group1")
        })
    }

    private val defaultInputContext = buildFlowEventContext<Any>(checkpoint, payload)
    private val outputContext = buildFlowEventContext<Any>(checkpoint, payload)

    private val startFlowEventHandler = mock<FlowEventHandler<Any>>().apply {
        whenever(preProcess(defaultInputContext)).thenReturn(outputContext)
    }

    private val externalEventResponseEventHandler = mock<FlowEventHandler<Any>>().apply {
        whenever(preProcess(any())).thenReturn(outputContext)
    }

    private val flowGlobalPostProcessor = mock<FlowGlobalPostProcessor>().apply {
        whenever(postProcess(defaultInputContext)).thenReturn(outputContext)
    }

    private val mockFlowExecutionPipelineStage = mock<FlowExecutionPipelineStage>().apply {
        whenever(runFlow(any(), any(), any())).thenReturn(outputContext)
    }

    private val virtualNodeInfo = mock<VirtualNodeInfo>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>().apply {
        whenever(get(any())).thenReturn(virtualNodeInfo)
    }

    private fun buildPipeline(inputContext: FlowEventContext<Any> = defaultInputContext): FlowEventPipelineImpl {
        return FlowEventPipelineImpl(
            mapOf(
                StartFlow::class.java to startFlowEventHandler,
                ExternalEventResponse::class.java to externalEventResponseEventHandler
            ),
            mockFlowExecutionPipelineStage,
            flowGlobalPostProcessor,
            inputContext,
            virtualNodeInfoReadService
        )
    }

    @Test
    fun `pipeline exits if flow operational status is inactive`() {
        val mockCheckpoint = mock<FlowCheckpoint> {
            whenever(it.doesExist).thenReturn(true)
            whenever(it.holdingIdentity).thenReturn(mockHoldingIdentity)
        }
        val mockContext = mock<FlowEventContext<Any>> {
            whenever(it.checkpoint).thenReturn(mockCheckpoint)
        }
        val pipeline =
            FlowEventPipelineImpl(
                mapOf(),
                mock(),
                mock(),
                mockContext,
                virtualNodeInfoReadService
            )

        val mockVirtualNode = mock<VirtualNodeInfo> {
            whenever(it.flowOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        }
        whenever(virtualNodeInfoReadService.get(mockHoldingIdentity)).thenReturn(mockVirtualNode)

        assertThrows<FlowMarkedForKillException> {
            pipeline.virtualNodeFlowOperationalChecks()
        }
    }

    @Test
    fun `pipeline exits if flow start operational status is inactive`() {
        val mockCheckpoint = mock<FlowCheckpoint> {
            whenever(it.doesExist).thenReturn(true)
            whenever(it.holdingIdentity).thenReturn(mockHoldingIdentity)
        }
        val mockContext = mock<FlowEventContext<Any>> {
            whenever(it.checkpoint).thenReturn(mockCheckpoint)
        }
        val pipeline =
            FlowEventPipelineImpl(
                mapOf(),
                mock(),
                mock(),
                mockContext,
                virtualNodeInfoReadService
            )

        val mockVirtualNode = mock<VirtualNodeInfo> {
            whenever(it.flowStartOperationalStatus).thenReturn(OperationalStatus.INACTIVE)
        }
        whenever(virtualNodeInfoReadService.get(mockHoldingIdentity)).thenReturn(mockVirtualNode)

        assertThrows<FlowMarkedForKillException> {
            pipeline.virtualNodeFlowOperationalChecks()
        }
    }

    @Test
    fun `execute flow invokes the execute flow pipeline stage`() {
        val pipeline = buildPipeline()
        pipeline.executeFlow(runOrContinueTimeout)
        verify(mockFlowExecutionPipelineStage).runFlow(eq(defaultInputContext), eq(runOrContinueTimeout), any())
    }

    @Test
    fun `globalPostProcessing calls the FlowGlobalPostProcessor when output is set`() {
        val pipeline = buildPipeline()
        assertEquals(outputContext, pipeline.globalPostProcessing().context)
        verify(flowGlobalPostProcessor).postProcess(defaultInputContext)
    }

    @Test
    fun `globalPostProcessing calls the FlowGlobalPostProcessor when output is not set`() {
        val pipeline = buildPipeline()
        assertEquals(outputContext, pipeline.globalPostProcessing().context)
        verify(flowGlobalPostProcessor).postProcess(defaultInputContext)
    }
}
