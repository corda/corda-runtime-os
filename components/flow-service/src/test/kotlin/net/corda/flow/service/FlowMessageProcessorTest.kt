package net.corda.flow.service

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.flow.service.exception.FlowHospitalException
import net.corda.messaging.api.records.Record
import net.corda.sandbox.service.SandboxService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class FlowMessageProcessorTest {
    private val flowManager: FlowManager = mock()
    private val sandboxService: SandboxService = mock()
    private val holdingIdentity = HoldingIdentity("", "")

    @Test
    fun `Start RPC flow`() {
        val flowKey = FlowKey("1", holdingIdentity)
        val startRPCFlow = Record(
            "Topic1", flowKey, FlowEvent(
                flowKey, StartRPCFlow(
                    "", "","", holdingIdentity, Instant.now(), ""
                )
            )
        )
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService)

        doReturn(FlowResult(null, emptyList())).whenever(flowManager)
            .startInitiatingFlow(any(), any(), anyOrNull())

        flowMessageProcessor.onNext(null, startRPCFlow)

        verify(sandboxService, times(1)).getSandboxGroupFor(any(), any(), any())
        verify(flowManager, times(1)).startInitiatingFlow(any(), any(), anyOrNull())
    }

    @Test
    fun `Start RPC flow non null state`() {
        val flowKey = FlowKey("1", holdingIdentity)
        val startRPCFlow = Record("Topic1", flowKey, FlowEvent(flowKey, StartRPCFlow("", "", "", holdingIdentity, Instant.now(),
            "")))
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService)

        doReturn(FlowResult(null, emptyList())).whenever(flowManager)
            .startInitiatingFlow(any(), any(), any())

        val state = Checkpoint()
        val result = flowMessageProcessor.onNext(state, startRPCFlow)
        assertThat(result.updatedState).isEqualTo(state)
        assertThat(result.responseEvents).isEmpty()

        verify(sandboxService, times(0)).getSandboxGroupFor(any(), any(), any())
        verify(flowManager, times(0)).startInitiatingFlow(any(), any(), any())
    }

    @Test
    fun `Null event`() {
        val flowKey = FlowKey("1", holdingIdentity)
        val flowEvent: FlowEvent? = null
        val startRPCFlow = Record("Topic1", flowKey, flowEvent)
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService)

        val state = Checkpoint()
        assertThrows<FlowHospitalException> {
            flowMessageProcessor.onNext(state, startRPCFlow)
        }

        verify(sandboxService, times(0)).getSandboxGroupFor(any(), any(), any())
        verify(flowManager, times(0)).startInitiatingFlow(any(), any(), any())
    }

    @Test
    fun `Wakeup flow`() {
        val flowKey = FlowKey("1", holdingIdentity)
        val wakeupFlow = Record("Topic1", flowKey, FlowEvent(flowKey, Wakeup("flowName")))
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService)
        doReturn(FlowResult(null, emptyList())).whenever(flowManager).wakeFlow(any(), any(), any(), anyOrNull())

        flowMessageProcessor.onNext(Checkpoint(flowKey, null, "cpidId", null, emptyList()), wakeupFlow)

        verify(sandboxService, times(1)).getSandboxGroupFor(any(), any(), any())
        verify(flowManager, times(1)).wakeFlow(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `Wakeup flow no state`() {
        val flowKey = FlowKey("1",  holdingIdentity)
        val wakeupFlow = Record("Topic1", flowKey, FlowEvent(flowKey, Wakeup("flowName")))
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService)

        assertThrows<FlowHospitalException> {
            flowMessageProcessor.onNext(null, wakeupFlow)
        }
        verify(sandboxService, times(0)).getSandboxGroupFor(any(), any(), any())
        verify(flowManager, times(0)).wakeFlow(any(), any(), any(), anyOrNull())
    }
}
