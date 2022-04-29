package net.corda.flow.testing.context

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*

class OutputAssertionsImpl(
    private val flowId: String,
    private val sessionInitiatingIdentity: HoldingIdentity? = null,
    private val sessionInitiatedIdentity: HoldingIdentity? = null,
) : OutputAssertions {

    private companion object {
        val log = contextLogger()
    }

    val asserts = mutableListOf<(TestRun) -> Unit>()

    override fun sessionAckEvent(
        flowId: String,
        sessionId: String,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ) {
        asserts.add { testRun ->
            assertNotNull(testRun.response, "Test run response value")
            val eventRecords = getMatchedFlowMapperEventRecords(testRun.response!!)
            assertTrue(eventRecords.any(), "Expected at least one event record")

            val sessionEvents =
                getMatchedSessionEvents(
                    sessionId,
                    initiatingIdentity ?: sessionInitiatingIdentity!!,
                    initiatingIdentity ?: sessionInitiatedIdentity!!,
                    eventRecords
                )
            assertTrue(sessionEvents.any(), "Expected at least one session event record")

            val foundAcks = sessionEvents.filter { it.payload is SessionAck }
            assertEquals(1, foundAcks.size, "Expected exactly least one ack event")
        }
    }

    override fun flowDidNotResume() {
        asserts.add { testRun ->
            assertNull(testRun.flowContinuation, "Not expecting the flow to resume")
        }
    }

    override fun flowResumedWithSessionData(vararg sessionData: Pair<String, ByteArray>) {
        asserts.add { testRun ->
            assertThat(testRun.flowContinuation).isInstanceOf(FlowContinuation.Run::class.java)
            val value = (testRun.flowContinuation as FlowContinuation.Run).value
            assertThat(value).isInstanceOf(Map::class.java)
            assertEquals(sessionData.toMap(), value as Map<*, *>)
        }
    }

    override fun wakeUpEvent() {
        asserts.add { testRun ->
            assertNotNull(testRun.response, "Test run response")

            val eventRecords = getMatchedFlowEventRecords(flowId, testRun.response!!)
            assertTrue(eventRecords.any(), "Expected at least one event record")

            val wakeupEvents = eventRecords.filter { it.payload is Wakeup }

            assertEquals(1, wakeupEvents.size, "Expected one wakeup event")
        }
    }

    override fun flowStatus(state: FlowStates, result: String?, error: Exception?) {
        asserts.add { testRun ->
            assertNotNull(testRun.response)
            assertTrue(
                testRun.response!!.responseEvents.any { matchStatusRecord(flowId, state, result, error, it) },
                "Expected Flow Status: ${state}, result = ${result ?: "NA"} error = ${error?.message ?: "NA"}"
            )
        }
    }

    private fun matchStatusRecord(
        flowId: String,
        state: FlowStates,
        result: String?,
        error: Exception?,
        record: Record<*, *>
    ): Boolean {
        if (record.value !is FlowStatus) {
            return false
        }

        val payload = record.value as FlowStatus
        return flowId == payload.flowId
                && payload.flowStatus == state
                && payload.result == result
                && payload.error?.errorMessage == error?.message
    }

    override fun nullStateRecord() {
        asserts.add {
            assertNull(it.response?.updatedState, "Expected to receive NULL for output state")
        }
    }

    private fun getMatchedFlowEventRecords(
        flowId: String,
        response: StateAndEventProcessor.Response<Checkpoint>
    ): List<FlowEvent> {
        return response.responseEvents
            .filter { it.key == flowId || it.topic == Schemas.Flow.FLOW_EVENT_TOPIC || it.value is FlowEvent }
            .map { it.value as FlowEvent }
    }

    private fun getMatchedFlowMapperEventRecords(
        response: StateAndEventProcessor.Response<Checkpoint>
    ): List<FlowMapperEvent> {
        return response.responseEvents
            .filter { it.topic == Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC && it.value is FlowMapperEvent }
            .map { it.value as FlowMapperEvent }
    }

    private fun getMatchedSessionEvents(
        sessionId: String,
        initiatingIdentity: HoldingIdentity,
        initiatedIdentity: HoldingIdentity,
        flowMapperEvents: List<FlowMapperEvent>
    ): List<SessionEvent> {
        val sessionEvents = flowMapperEvents.filter { it.payload is SessionEvent }.map { it.payload as SessionEvent }
        log.info("found ${sessionEvents.size} session events in ${flowMapperEvents.size} flow mapper events.")

        val foundEvents = sessionEvents.filter {
            it.sessionId == sessionId &&
                    it.initiatedIdentity == initiatedIdentity &&
                    it.initiatingIdentity == initiatingIdentity
        }

        log.info("found ${foundEvents.size} matching session events in ${flowMapperEvents.size} flow mapper events.")
        return foundEvents
    }
}