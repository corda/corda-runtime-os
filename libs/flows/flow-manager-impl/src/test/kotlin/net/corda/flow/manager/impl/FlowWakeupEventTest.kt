package net.corda.flow.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowFiber
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicEventPayloads
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicEvents
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicRecords
import net.corda.flow.manager.impl.acceptance.dsl.flowEventDSL
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.uncheckedCast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import net.corda.flow.manager.impl.acceptance.dsl.mockFlowEventProcessor

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

        val fiber = MockFlowFiber("flow id")

        mockFlowEventProcessor.addFlowFiber(fiber)

        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        val output1 = mockFlowEventProcessor.onNext(state = null, event = Record("topic", flowKey, startFlowEvent))

        assertNotNull(output1.updatedState)

        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = uncheckedCast(output1.responseEvents.single()))

        assertNotNull(output1.updatedState)

        fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = uncheckedCast(output2.responseEvents.single()))
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using startFlowProcessing function`() {
        val (fiber, output1) = mockFlowEventProcessor.startFlow()

        assertNotNull(output1.updatedState)

        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = uncheckedCast(output1.responseEvents.single()))

        assertNotNull(output1.updatedState)

        fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = uncheckedCast(output2.responseEvents.single()))
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using filterOutputFlowTopicRecords function`() {
        val (fiber, output1) = mockFlowEventProcessor.startFlow()

        assertNotNull(output1.updatedState)

        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = output1.filterOutputFlowTopicRecords().single())

        assertNotNull(output1.updatedState)

        fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = output2.filterOutputFlowTopicRecords().single())
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using filterOutputFlowTopicEvents and filterOutputFlowTopicEventPayloads functions`() {
        val (fiber, output1) = mockFlowEventProcessor.startFlow()

        assertNotNull(output1.updatedState)
        assertEquals(Wakeup(), output1.filterOutputFlowTopicEvents().single().payload)
        assertEquals(Wakeup(), output1.filterOutputFlowTopicEventPayloads().single())

        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        val output2 = mockFlowEventProcessor.onNext(state = output1.updatedState, event = output1.filterOutputFlowTopicRecords().single())
        fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))
        val output3 = mockFlowEventProcessor.onNext(state = output2.updatedState, event = output2.filterOutputFlowTopicRecords().single())
        assertNull(output3.updatedState)
    }

    @Test
    fun `keep waking up the flow using the FlowEventDSL`() {
        flowEventDSL {

            val fiber = flowFiber("flow id") {
                queueSuspension(FlowIORequest.ForceCheckpoint)
                queueSuspension(FlowIORequest.ForceCheckpoint)
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(startFlowEvent)
            // change to process all because this cant happen
            inputLastOutputEvent()
            inputLastOutputEvent()

            val output1 = processAll()
            assertNotNull(output1.last().updatedState)
            assertEquals(Wakeup(), output1.last().filterOutputFlowTopicEventPayloads().single())

            inputLastOutputEvent()
            fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))

            val output2 = processOne()
            assertNull(output2.updatedState)
            assertEquals(0, output2.filterOutputFlowTopicEventPayloads().size)

        }
    }

    @Test
    fun `keep waking up the flow using the FlowEventDSL and repeatSuspension`() {
        flowEventDSL {

            val fiber = flowFiber("flow id") {
                repeatSuspension(FlowIORequest.ForceCheckpoint, 3)
            }

            input(startFlowEvent)
            // change to process all because this cant happen
            inputLastOutputEvent()
            inputLastOutputEvent()

            val output1 = processAll()
            assertNotNull(output1.last().updatedState)
            assertEquals(Wakeup(), output1.last().filterOutputFlowTopicEventPayloads().single())

            inputLastOutputEvent()
            fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))

            val output2 = processOne()
            assertNull(output2.updatedState)
            assertEquals(0, output2.filterOutputFlowTopicEventPayloads().size)

        }
    }

    @Test
    fun `keep waking up multiple flows`() {
        flowEventDSL {

            startedFlowFiber("flow id 1") {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            startedFlowFiber("flow id 2") {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            inputLastOutputEvent()
            inputLastOutputEvent()

            val output = processAll()

            assertEquals(
                Wakeup(),
                output.last { it.updatedState?.flowKey?.flowId == "flow id 1" }.filterOutputFlowTopicEventPayloads().single()
            )
            assertEquals(
                Wakeup(),
                output.last { it.updatedState?.flowKey?.flowId == "flow id 2" }.filterOutputFlowTopicEventPayloads().single()
            )
        }
    }
}