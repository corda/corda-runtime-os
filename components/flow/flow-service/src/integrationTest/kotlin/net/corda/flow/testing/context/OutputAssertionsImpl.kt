package net.corda.flow.testing.context

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class OutputAssertionsImpl(
    private val flowId: String,
    private val sessionInitiatingIdentity: HoldingIdentity? = null,
    private val sessionInitiatedIdentity: HoldingIdentity? = null,
) : OutputAssertions {

    private companion object {
        val log = contextLogger()
    }

    val asserts = mutableListOf<(TestRun) -> Unit>()

    override fun sessionAckEvents(vararg sessionIds: String, initiatingIdentity: HoldingIdentity?, initiatedIdentity: HoldingIdentity?) {
        asserts.add { testRun ->
            findAndAssertSessionEvents<SessionAck>(testRun, sessionIds.toList(), initiatingIdentity, initiatedIdentity)
        }
    }

    override fun sessionInitEvents(vararg sessionIds: String, initiatingIdentity: HoldingIdentity?, initiatedIdentity: HoldingIdentity?) {
        asserts.add { testRun ->
            findAndAssertSessionEvents<SessionInit>(testRun, sessionIds.toList(), initiatingIdentity, initiatedIdentity)
        }
    }

    override fun sessionDataEvents(
        vararg sessionToPayload: Pair<String, ByteArray>,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ) {
        asserts.add { testRun ->
            val foundSessionToPayload = findAndAssertSessionEvents<SessionData>(
                testRun,
                sessionToPayload.map { it.first },
                initiatingIdentity,
                initiatedIdentity
            ).associate { it.sessionId to (it.payload as SessionData).payload.array() }

            assertEquals(
                sessionToPayload.toMap(),
                foundSessionToPayload,
                "Expected sessions to send data events containing: $sessionToPayload but found $foundSessionToPayload instead"
            )

        }
    }

    override fun sessionCloseEvents(vararg sessionIds: String, initiatingIdentity: HoldingIdentity?, initiatedIdentity: HoldingIdentity?) {
        asserts.add { testRun ->
            findAndAssertSessionEvents<SessionClose>(testRun, sessionIds.toList(), initiatingIdentity, initiatedIdentity)
        }
    }

    override fun sessionErrorEvents(vararg sessionIds: String, initiatingIdentity: HoldingIdentity?, initiatedIdentity: HoldingIdentity?) {
        asserts.add { testRun ->
            findAndAssertSessionEvents<SessionError>(testRun, sessionIds.toList(), initiatingIdentity, initiatedIdentity)
        }
    }

    override fun flowDidNotResume() {
        asserts.add { testRun ->
            assertNull(testRun.flowContinuation, "Not expecting the flow to resume")
        }
    }

    override fun flowResumedWith(value: Any) {
        asserts.add { testRun ->
            assertInstanceOf(FlowContinuation.Run::class.java, testRun.flowContinuation)
            assertEquals(value, (testRun.flowContinuation as FlowContinuation.Run).value)
        }
    }

    override fun <T: Throwable> flowResumedWithError(exceptionClass: Class<T>) {
        asserts.add { testRun ->
            assertInstanceOf(FlowContinuation.Error::class.java, testRun.flowContinuation)
            assertInstanceOf(exceptionClass, (testRun.flowContinuation as FlowContinuation.Error).exception)
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

    override fun noFlowEvents() {
        asserts.add { testRun ->
            val eventRecords = getMatchedFlowEventRecords(flowId, testRun.response!!)
            assertEquals(0, eventRecords.size, "Matched FlowEvents")
        }
    }

    override fun checkpointHasRetry(expectedCount: Int) {
        asserts.add { testRun ->
            assertThat(testRun.response?.updatedState?.retryState).isNotNull
            val retry = testRun.response!!.updatedState!!.retryState

            assertThat(retry.retryCount).isEqualTo(expectedCount)

            /** we can't assert the event the second time around as it's a wakeup event (initially)
             * so the testRun.event, which records the event we send into the system, will not match
             * the retry.failedEvent as internally the pipeline switches from the wakeup to the event that needs
             * to be retried.
             */
            if (retry.retryCount == 1) {
                assertThat(retry.failedEvent).isEqualTo(testRun.event.value)
            }
        }
    }

    override fun checkpointDoesNotHaveRetry() {
        asserts.add { testRun ->
            assertThat(testRun.response?.updatedState?.retryState).isNull()
        }
    }

    override fun flowStatus(state: FlowStates, result: String?, errorType: String?, errorMessage: String?) {
        asserts.add { testRun ->
            assertNotNull(testRun.response)
            assertTrue(
                testRun.response!!.responseEvents.any {
                    matchStatusRecord(flowId, state, result, errorType, errorMessage, it)
                },
                "Expected Flow Status: ${state}, result = ${result ?: "NA"} error = ${errorMessage ?: "NA"}"
            )
        }
    }

    private inline fun <reified T> findAndAssertSessionEvents(
        testRun: TestRun,
        sessionIds: List<String>,
        initiatingIdentity: HoldingIdentity?,
        initiatedIdentity: HoldingIdentity?
    ): List<SessionEvent> {
        assertNotNull(testRun.response, "Test run response value")
        val eventRecords = getMatchedFlowMapperEventRecords(testRun.response!!)

        val sessionEvents =
            getMatchedSessionEvents(
                initiatingIdentity ?: sessionInitiatingIdentity!!,
                initiatedIdentity ?: sessionInitiatedIdentity!!,
                eventRecords
            )

        val filteredEvents = sessionEvents.filter { it.payload is T }
        val filteredSessionIds = filteredEvents.map { it.sessionId }

        assertEquals(
            sessionIds,
            filteredSessionIds,
            "Expected session ids: $sessionIds but found $filteredSessionIds when expecting ${T::class.simpleName} events"
        )

        return filteredEvents
    }

    private fun matchStatusRecord(
        flowId: String,
        state: FlowStates,
        result: String?,
        errorType: String?,
        errorMessage: String?,
        record: Record<*, *>
    ): Boolean {
        if (record.value !is FlowStatus) {
            return false
        }

        val payload = record.value as FlowStatus
        return flowId == payload.flowId
                && payload.flowStatus == state
                && payload.result == result
                && payload.error?.errorType == errorType
                && payload.error?.errorMessage == errorMessage
    }

    override fun nullStateRecord() {
        asserts.add {
            assertNull(it.response?.updatedState, "Expected to receive NULL for output state")
        }
    }

    override fun markedForDlq() {
        asserts.add {
            assertThat(it.response?.markForDLQ).isTrue
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
        initiatingIdentity: HoldingIdentity,
        initiatedIdentity: HoldingIdentity,
        flowMapperEvents: List<FlowMapperEvent>
    ): List<SessionEvent> {
        val sessionEvents = flowMapperEvents.filter { it.payload is SessionEvent }.map { it.payload as SessionEvent }
        log.info("Found ${sessionEvents.size} session events in ${flowMapperEvents.size} flow mapper events.")

        return sessionEvents
            .filter { it.initiatedIdentity == initiatedIdentity }
            .filter { it.initiatingIdentity == initiatingIdentity }
    }
}