package net.corda.components.flow.service

import net.corda.components.sandbox.service.SandboxService
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Test
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

    @Test
    fun `Start RPC flow`() {
        val flowKey = FlowKey("1", HoldingIdentity())
        val startRPCFlow = Record("Topic1", flowKey, FlowEvent(flowKey, StartRPCFlow("", "", "", HoldingIdentity(), Instant.now(), "")))
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService, "")

        doReturn(FlowResult(null, emptyList())).whenever(flowManager)
            .startInitiatingFlow(any(), any(), any(), any(), anyOrNull())

        flowMessageProcessor.onNext(null, startRPCFlow)

        verify(sandboxService, times(1)).getSandboxGroupFor(any(), any())
        verify(flowManager, times(1)).startInitiatingFlow(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `Wakeup flow`() {
        val flowKey = FlowKey("1", HoldingIdentity())
        val wakeupFlow = Record("Topic1", flowKey, FlowEvent(flowKey, Wakeup("flowName", "cpiId")))
        val flowMessageProcessor = FlowMessageProcessor(flowManager, sandboxService, "")
        doReturn(FlowResult(null, emptyList())).whenever(flowManager).wakeFlow(any(), any(), any(), anyOrNull())

        flowMessageProcessor.onNext(Checkpoint(), wakeupFlow)

        verify(sandboxService, times(1)).getSandboxGroupFor(any(), any())
        verify(flowManager, times(1)).wakeFlow(any(), any(), any(), anyOrNull())
    }
}
