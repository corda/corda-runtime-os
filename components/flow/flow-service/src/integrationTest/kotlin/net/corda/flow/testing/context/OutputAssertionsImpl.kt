package net.corda.flow.testing.context

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityRequest
import net.corda.flow.fiber.FlowContinuation
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory

class OutputAssertionsImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val stringDeserializer: CordaAvroDeserializer<String>,
    private val byteArrayDeserializer: CordaAvroDeserializer<ByteArray>,
    private val anyDeserializer: CordaAvroDeserializer<Any>,
    private val flowId: String,
    private val sessionInitiatingIdentity: HoldingIdentity? = null,
    private val sessionInitiatedIdentity: HoldingIdentity? = null,
) : OutputAssertions {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

    override fun externalEvent(topic: String, key: Any, payload: Any) {
        asserts.add { testRun ->
            assertNotNull(testRun.response, "Test run response value")

            val serializedKey = serializer.serialize(key)
            val serializedPayload = serializer.serialize(payload)

            val externalEventsToTopic = testRun.response!!.responseEvents.filter { it.topic == topic }

            assertEquals(
                1,
                externalEventsToTopic.size,
                "Expected to find a single external event sent to topic: $topic but found $externalEventsToTopic"
            )

            assertArrayEquals(
                serializedKey!!,
                externalEventsToTopic.single().key as ByteArray) {
                "Expected the external event to have a key of $key but was ${
                    avroDeserializeTo(externalEventsToTopic.single().key as ByteArray, key)
                }"
            }

            assertArrayEquals(
                serializedPayload,
                externalEventsToTopic.single().value as ByteArray) {
                "Expected the external event to have a payload of $payload but was ${
                    avroDeserializeTo(externalEventsToTopic.single().value as ByteArray, payload)
                }"
            }
        }
    }

    private fun avroDeserializeTo(bytes: ByteArray, serializeToMatchingType: Any): Any? {
        return when (serializeToMatchingType) {
            is String -> stringDeserializer.deserialize(bytes)
            is ByteArray -> byteArrayDeserializer.deserialize(bytes)
            else -> anyDeserializer.deserialize(bytes)
        }
    }

    override fun noExternalEvent(topic: String) {
        asserts.add { testRun ->
            assertNotNull(testRun.response, "Test run response value")

            val externalEventsToTopic = testRun.response!!.responseEvents.filter { it.topic == topic }

            assertEquals(
                0,
                externalEventsToTopic.size,
                "Expected to find no external event sent to topic: $topic but found $externalEventsToTopic"
            )
        }
    }

    override fun scheduleFlowMapperCleanupEvents(vararg key: String) {
        asserts.add { testRun ->
            assertNotNull(testRun.response, "Test run response value")
            val eventRecords = getMatchedFlowMapperEventRecords(testRun.response!!)
            val filteredEvents = eventRecords.filter { it.value?.payload is ScheduleCleanup }
            val filteredKeys = filteredEvents.map { it.key }

            assertEquals(
                key.toList(),
                filteredKeys,
                "Expected keys: ${key.toList()} but found $filteredKeys when expecting ${ScheduleCleanup::class.simpleName} events"
            )
        }
    }

    override fun flowDidNotResume() {
        asserts.add { testRun ->
            assertNull(testRun.flowContinuation, "Not expecting the flow to resume")
        }
    }

    override fun flowResumedWithData(value: Map<String, ByteArray>) {
        asserts.add { testRun ->
            assertInstanceOf(FlowContinuation.Run::class.java, testRun.flowContinuation)
            val resumedWith = (testRun.flowContinuation as FlowContinuation.Run).value

            if (resumedWith is Map<*, *>) {
                assertEquals(value.keys, resumedWith.keys)
                value.values.zip(resumedWith.values).forEach { pair ->
                    assertArrayEquals(pair.component1(), pair.component2() as ByteArray,
                        "Expected flow to resume with $value but was $resumedWith"
                    )
                }
            }
        }
    }

    override fun flowResumedWith(value: Any?) {
        asserts.add { testRun ->
            assertInstanceOf(FlowContinuation.Run::class.java, testRun.flowContinuation)
            val resumedWith = (testRun.flowContinuation as FlowContinuation.Run).value
            assertEquals(value, resumedWith, "Expected flow to resume with $value but was $resumedWith")
        }
    }

    override fun <T : Throwable> flowResumedWithError(exceptionClass: Class<T>) {
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

    override fun noWakeUpEvent() {
        asserts.add { testRun ->
            assertNotNull(testRun.response, "Test run response")

            val eventRecords = getMatchedFlowEventRecords(flowId, testRun.response!!)
            val wakeupEvents = eventRecords.filter { it.payload is Wakeup }

            assertEquals(0, wakeupEvents.size, "Expected no wakeup event")
        }
    }

    override fun hasPendingUserException() {
        asserts.add{ testRun ->
            assertThat(testRun.response?.updatedState?.pipelineState?.pendingPlatformError).isNotNull()
        }
    }

    override fun noPendingUserException() {
        asserts.add{ testRun ->
            assertThat(testRun.response?.updatedState?.pipelineState?.pendingPlatformError).isNull()
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
            assertThat(testRun.response?.updatedState?.pipelineState?.retryState).isNotNull
            val retry = testRun.response!!.updatedState!!.pipelineState!!.retryState

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
            assertThat(testRun.response?.updatedState?.pipelineState?.retryState).isNull()
        }
    }

    override fun flowStatus(state: FlowStates, result: String?, errorType: String?, errorMessage: String?) {
        asserts.add { testRun ->
            assertNotNull(testRun.response)
            assertTrue(
                testRun.response!!.responseEvents.any {
                    matchStatusRecord(flowId, state, result, errorType, errorMessage, it)
                },
                "Expected Flow Status: ${state}, result = ${result ?: "NA"}, errorType = ${errorType ?: "NA"}, error = ${errorMessage ?: "NA"}"
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
        val eventRecords = getMatchedFlowMapperEventRecords(testRun.response!!).map { it.value as FlowMapperEvent }

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

    override fun entityRequestSent(expectedRequestPayload: Any) {
        asserts.add { testRun ->
            val response = testRun.response
            assertNotNull(response, "Test run response value")

            val entityRequests = response!!.responseEvents.filter {
                it.value is EntityRequest
            }

            assertTrue(entityRequests.isNotEmpty(), "No entity request found in response output events")
            assertTrue(entityRequests.size == 1, "More than one entity request found in response output events")
            val foundEntityRequest = entityRequests.first().value as EntityRequest

            assertNotNull(foundEntityRequest, "No entity request found in response events.")
            val outputRequestPayload = foundEntityRequest.request
            assertTrue(outputRequestPayload::class.java == expectedRequestPayload::class.java,
                "Entity request found is of the wrong type. Expected ${expectedRequestPayload::class.java}, found: ${expectedRequestPayload::class.java}")
            assertTrue(expectedRequestPayload == outputRequestPayload, "Entity request payload found does not match the expected payload")
        }
    }

    override fun noEntityRequestSent() {
        asserts.add { testRun ->
            val response = testRun.response
            assertNotNull(response, "Test run response value")

            val entityRequests = response!!.responseEvents.filter {
                it.value is EntityRequest
            }
            assertTrue(entityRequests.isEmpty(), "Entity request found in response events.")
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
    ): List<Record<*, FlowMapperEvent>> {
        return response.responseEvents
            .filter { it.topic == Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC && it.value is FlowMapperEvent }
            .map { uncheckedCast(it) }
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