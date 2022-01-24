package net.corda.flow.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.mock.filterOutputFlowTopicEventPayloads
import net.corda.flow.manager.mock.filterOutputFlowTopicEvents
import net.corda.flow.manager.mock.filterOutputFlowTopicRecords
import net.corda.flow.manager.mock.flowEventDSL
import net.corda.flow.manager.mock.mockFlowEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class FlowWakeupEventTest {

    private val mockFlowEventProcessor = mockFlowEventProcessor()

    private val startRPCFlowPayload = StartRPCFlow.newBuilder()
        .setClientId("client id")
        .setCpiId("cpi id")
        .setFlowClassName("flow class name")
        .setRpcUsername(HoldingIdentity("x500 name", "group id"))
        .setTimestamp(Instant.now())
        .setJsonArgs(" { \"json\": \"args\" }")
        .build()

    private val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))

    private val startFlowEvent = FlowEvent(flowKey, startRPCFlowPayload)

    @Test
    fun `keep waking up the flow`() {
        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.ForceCheckpoint)
        val output1 = mockFlowEventProcessor.onNext(state = null, event = Record("topic", flowKey, startFlowEvent))

        assertNotNull(output1.updatedState)

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = uncheckedCast(output1.responseEvents.single()))

        assertNotNull(output1.updatedState)

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = uncheckedCast(output2.responseEvents.single()))
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using startFlowProcessing function`() {
        val output1 = mockFlowEventProcessor.startFlowProcessing()

        assertNotNull(output1.updatedState)

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = uncheckedCast(output1.responseEvents.single()))

        assertNotNull(output1.updatedState)

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = uncheckedCast(output2.responseEvents.single()))
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using filterOutputFlowTopicRecords function`() {
        val output1 = mockFlowEventProcessor.startFlowProcessing()

        assertNotNull(output1.updatedState)

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = output1.filterOutputFlowTopicRecords().single())

        assertNotNull(output1.updatedState)

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = output2.filterOutputFlowTopicRecords().single())
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using filterOutputFlowTopicEvents and filterOutputFlowTopicEventPayloads functions`() {
        val output1 = mockFlowEventProcessor.startFlowProcessing()

        assertNotNull(output1.updatedState)
        assertEquals(Wakeup(), output1.filterOutputFlowTopicEvents().single().payload)
        assertEquals(Wakeup(), output1.filterOutputFlowTopicEventPayloads().single())

        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = output1.filterOutputFlowTopicRecords().single())
        mockFlowEventProcessor.setNextFlowIORequest(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = output2.filterOutputFlowTopicRecords().single())
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using the FlowEventDSL`() {
        flowEventDSL {
            input(startFlowEvent)
            inputLastOutputEvent()
            inputLastOutputEvent()
            suspend(FlowIORequest.ForceCheckpoint)
            suspend(FlowIORequest.ForceCheckpoint)
            suspend(FlowIORequest.ForceCheckpoint)

            val output1 = processAll()
            assertNotNull(output1.last().updatedState)
            assertEquals(Wakeup(), output1.last().filterOutputFlowTopicEventPayloads().single())

            inputLastOutputEvent()
            suspend(FlowIORequest.FlowFinished(Unit))

            val output2 = processOne()
            assertNull(output2.updatedState)
            assertEquals(0, output2.filterOutputFlowTopicEventPayloads().size)

        }
    }
}