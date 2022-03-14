package net.corda.flow.acceptance

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.acceptance.dsl.filterOutputFlowTopicEventPayloads
import net.corda.flow.acceptance.dsl.flowEventDSL
import net.corda.flow.fiber.FlowIORequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FlowEventDSLTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val ANOTHER_FLOW_ID = "another flow id"
    }

    private val startRPCFlowPayload = StartFlow.newBuilder()
        .setStartContext(getBasicFlowStartContext())
        .setFlowStartArgs(" { \"json\": \"args\" }")
        .build()

    private val flowKey = FlowKey(FLOW_ID, HoldingIdentity("x500 name", "group id"))

    private val startFlowEvent = FlowEvent(flowKey, startRPCFlowPayload)

    @Test
    fun `process one step after creating a flow fiber, queuing a suspension and inputting an event`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(startFlowEvent)

            val (checkpoint, responseEvents) = processOne()
            assertNotNull(checkpoint)
            assertEquals(Wakeup(), (responseEvents.single().value as FlowEvent).payload)
        }
    }

    @Test
    fun `process all steps after creating a flow fiber, queuing a suspension and inputting an event`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(startFlowEvent)

            val (checkpoint, responseEvents) = processAll().single()
            assertNotNull(checkpoint)
            assertEquals(Wakeup(), (responseEvents.single().value as FlowEvent).payload)
        }
    }

    @Test
    fun `process one step after creating a started flow fiber, queuing a suspension and inputting an event`() {
        flowEventDSL {

            startedFlowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(FlowEvent(flowKey, Wakeup()))

            val (checkpoint, responseEvents) = processOne()
            assertNotNull(checkpoint)
            assertEquals(Wakeup(), (responseEvents.single().value as FlowEvent).payload)
        }
    }

    @Test
    fun `process all steps after creating a flow fiber, queuing suspensions and inputting events`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
                queueSuspension(FlowIORequest.ForceCheckpoint)
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(startFlowEvent)
            input(FlowEvent(flowKey, Wakeup()))
            input(FlowEvent(flowKey, Wakeup()))

            val responses = processAll()
            assertEquals(3, responses.size)
            assertNotNull(responses.last().updatedState)
            assertEquals(Wakeup(), (responses.last().responseEvents.single().value as FlowEvent).payload)
        }
    }

    @Test
    fun `inputLastOutputEvent processes the last event output from the event processor`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
                queueSuspension(FlowIORequest.ForceCheckpoint)
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(startFlowEvent)
            inputLastOutputEvent()
            inputLastOutputEvent()

            val responses = processAll()
            assertEquals(3, responses.size)
            assertNotNull(responses.last().updatedState)
            assertEquals(Wakeup(), (responses.last().responseEvents.single().value as FlowEvent).payload)
        }
    }

    @Test
    fun `multiple flow fibers can be interacted with`() {
        flowEventDSL {

            startedFlowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            startedFlowFiber(ANOTHER_FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            input(FlowEvent(FlowKey(FLOW_ID, HoldingIdentity("x500 name", "group id")), Wakeup()))
            input(FlowEvent(FlowKey(ANOTHER_FLOW_ID, HoldingIdentity("x500 name", "group id")), Wakeup()))

            val output = processAll()

            assertEquals(
                Wakeup(),
                output.last { it.updatedState?.flowKey?.flowId == FLOW_ID }.filterOutputFlowTopicEventPayloads().single()
            )
            assertEquals(
                Wakeup(),
                output.last { it.updatedState?.flowKey?.flowId == ANOTHER_FLOW_ID }.filterOutputFlowTopicEventPayloads().single()
            )
        }
    }

    @Test
    fun `inputLastOutputEvent works with multiple flow fibers`() {
        flowEventDSL {

            startedFlowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            startedFlowFiber(ANOTHER_FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            inputLastOutputEvent()
            inputLastOutputEvent()

            val output = processAll()

            assertEquals(
                Wakeup(),
                output.last { it.updatedState?.flowKey?.flowId == FLOW_ID }.filterOutputFlowTopicEventPayloads().single()
            )
            assertEquals(
                Wakeup(),
                output.last { it.updatedState?.flowKey?.flowId == ANOTHER_FLOW_ID }.filterOutputFlowTopicEventPayloads().single()
            )
        }
    }

    @Test
    fun `cannot process one step when no events are input`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            Assertions.assertThatThrownBy { processOne() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No input flow events have been setup")
        }
    }

    @Test
    fun `cannot process multiple steps when no events are input`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            Assertions.assertThatThrownBy { processAll() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No input flow events have been setup")
        }
    }

    @Test
    fun `inputLastOutputEvent requires existing output events when processing`() {
        flowEventDSL {

            flowFiber(FLOW_ID) {
                queueSuspension(FlowIORequest.ForceCheckpoint)
            }

            inputLastOutputEvent()

            Assertions.assertThatThrownBy { processAll() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Trying to process an output flow event returned from the processor but none exist")
        }
    }
}