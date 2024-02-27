package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.FLOW_ID_1
import net.corda.flow.REQUEST_ID_1
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_SESSION_EXPIRY_KEY
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.KeyValueStore
import net.corda.libs.statemanager.api.Metadata
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.SESSION_TIMEOUT_WINDOW
import net.corda.session.manager.Constants
import net.corda.session.manager.SessionManager
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class FlowGlobalPostProcessorImplTest {

    private companion object {
        const val SESSION_ID_1 = "s1"
        const val SESSION_ID_2 = "s2"
        const val SESSION_ID_3 = "s3"
    }

    private val sessionState1 = SessionState().apply {
        this.sessionId = SESSION_ID_1
        this.hasScheduledCleanup = false
        this.counterpartyIdentity = ALICE_X500_HOLDING_IDENTITY
    }
    private val sessionState2 = SessionState().apply {
        this.sessionId = SESSION_ID_2
        this.hasScheduledCleanup = false
        this.counterpartyIdentity = ALICE_X500_HOLDING_IDENTITY
    }
    private val sessionState3 = SessionState().apply {
        this.sessionId = SESSION_ID_3
        this.status = SessionStateType.CREATED
        this.counterpartyIdentity = BOB_X500_HOLDING_IDENTITY
    }
    private val sessionEvent1 = SessionEvent().apply {
        this.sessionId = SESSION_ID_1
        this.sequenceNum = 1
        this.payload = SessionData()
    }
    private val sessionEvent2 = SessionEvent().apply {
        this.sessionId = SESSION_ID_1
        this.sequenceNum = 2
        this.payload = SessionData()
    }
    private val sessionEvent3 = SessionEvent().apply {
        this.sessionId = SESSION_ID_2
        this.sequenceNum = 1
        this.payload = SessionData()
    }
    private val sessionEvent4 = SessionEvent().apply {
        this.sessionId = SESSION_ID_3
        this.sequenceNum = 1
        this.payload = SessionData()
    }
    private val sessionRecord1 = Record("t", SESSION_ID_1, FlowMapperEvent(sessionEvent1))
    private val sessionRecord2 = Record("t", SESSION_ID_1, FlowMapperEvent(sessionEvent2))
    private val sessionRecord3 = Record("t", SESSION_ID_2, FlowMapperEvent(sessionEvent3))
    private val scheduleCleanupRecord1 = Record("t", SESSION_ID_1, FlowMapperEvent(ScheduleCleanup(1000)))
    private val scheduleCleanupRecord2 = Record("t", SESSION_ID_2, FlowMapperEvent(ScheduleCleanup(1000)))
    private val externalEventRecord = Record("t", "key", byteArrayOf(1, 2, 3))
    private val sessionManager = mock<SessionManager>()
    private val externalEventManager = mock<ExternalEventManager>()
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider>()
    private val membershipGroupReader = mock<MembershipGroupReader>()
    private val checkpoint = mock<FlowCheckpoint>()
    private val testContext = buildFlowEventContext(checkpoint, Any())
    private val flowGlobalPostProcessor = FlowGlobalPostProcessorImpl(
        externalEventManager,
        sessionManager,
        flowRecordFactory,
        membershipGroupReaderProvider
    )

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        whenever(checkpoint.flowKey).thenReturn(FlowKey(FLOW_ID_1, ALICE_X500_HOLDING_IDENTITY))
        whenever(checkpoint.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.pendingPlatformError).thenReturn(null)
        whenever(
            sessionManager.getMessagesToSend(
                eq(sessionState1),
                any(),
                eq(testContext.flowConfig),
                eq(ALICE_X500_HOLDING_IDENTITY)
            )
        ).thenReturn(sessionState1 to listOf(sessionEvent1, sessionEvent2))
        whenever(
            sessionManager.getMessagesToSend(
                eq(sessionState2),
                any(),
                eq(testContext.flowConfig),
                eq(ALICE_X500_HOLDING_IDENTITY)
            )
        ).thenReturn(sessionState2 to listOf(sessionEvent3))

        whenever(
            sessionManager.getMessagesToSend(
                eq(sessionState3),
                any(),
                eq(testContext.flowConfig),
                eq(ALICE_X500_HOLDING_IDENTITY)
            )
        ).thenReturn(sessionState3 to listOf(sessionEvent4))

        whenever(flowRecordFactory.createFlowMapperEventRecord(sessionEvent1.sessionId, sessionEvent1)).thenReturn(
            sessionRecord1
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(sessionEvent2.sessionId, sessionEvent2)).thenReturn(
            sessionRecord2
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(sessionEvent3.sessionId, sessionEvent3)).thenReturn(
            sessionRecord3
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq(SESSION_ID_1), any<ScheduleCleanup>())).thenReturn(
            scheduleCleanupRecord1
        )
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq(SESSION_ID_2), any<ScheduleCleanup>())).thenReturn(
            scheduleCleanupRecord2
        )
        whenever(membershipGroupReaderProvider.getGroupReader(anyOrNull())).thenReturn(membershipGroupReader)
        whenever(membershipGroupReader.lookup(ALICE_X500_NAME)).thenReturn(mock())
        whenever(membershipGroupReader.lookup(BOB_X500_NAME)).thenReturn(null)

        setOf(sessionState1, sessionState2, sessionState3).forEach {
            it.sessionStartTime = Instant.now()
            it.lastReceivedMessageTime = Instant.now()
        }
    }

    @Test
    fun `Adds output records containing session events to send to peers`() {
        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).containsOnly(sessionRecord1, sessionRecord2, sessionRecord3)
    }

    @Test
    fun `Updates session states`() {
        flowGlobalPostProcessor.postProcess(testContext)
        verify(checkpoint).putSessionState(sessionState1)
        verify(checkpoint).putSessionState(sessionState2)
    }

    @Test
    fun `Does not update sessions when the checkpoint has been deleted`() {
        whenever(checkpoint.doesExist).thenReturn(false)
        flowGlobalPostProcessor.postProcess(testContext)
        verify(checkpoint, never()).putSessionState(sessionState1)
        verify(checkpoint, never()).putSessionState(sessionState2)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when there are CLOSED sessions`() {
        sessionState1.status = SessionStateType.CLOSED
        sessionState2.status = SessionStateType.CONFIRMED

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1)
        assertThat(outputContext.outputRecords).doesNotContain(scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertFalse(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when there are ERRORed sessions`() {
        sessionState1.status = SessionStateType.ERROR
        sessionState2.status = SessionStateType.CONFIRMED

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1)
        assertThat(outputContext.outputRecords).doesNotContain(scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertFalse(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when there are CLOSED and ERRORed sessions`() {
        sessionState1.status = SessionStateType.CLOSED
        sessionState2.status = SessionStateType.ERROR

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1, scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertTrue(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds no output records containing schedule cleanup events when there are no CLOSED or ERRORed or sessions`() {
        sessionState1.status = SessionStateType.CREATED
        sessionState2.status = SessionStateType.CONFIRMED

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).doesNotContain(scheduleCleanupRecord1, scheduleCleanupRecord2)
        assertFalse(sessionState1.hasScheduledCleanup)
        assertFalse(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Adds output records containing schedule cleanup events when when the checkpoint has been deleted`() {
        whenever(checkpoint.doesExist).thenReturn(false)
        sessionState1.status = SessionStateType.CLOSED
        sessionState2.status = SessionStateType.ERROR

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(scheduleCleanupRecord1, scheduleCleanupRecord2)
        assertTrue(sessionState1.hasScheduledCleanup)
        assertTrue(sessionState2.hasScheduledCleanup)
    }

    @Test
    fun `Clears pending platform errors`() {
        flowGlobalPostProcessor.postProcess(testContext)

        verify(checkpoint).clearPendingPlatformError()
    }

    @Test
    fun `Adds external event record when there is an external event to send`() {
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply { REQUEST_ID_1 }

        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.getEventToSend(eq(externalEventState), any(), any()))
            .thenReturn(updatedExternalEventState to externalEventRecord)

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).contains(externalEventRecord)
        verify(checkpoint).externalEventState = updatedExternalEventState
    }

    @Test
    fun `Does not add an external event record when there is no external event to send`() {
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply { REQUEST_ID_1 }

        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.getEventToSend(eq(externalEventState), any(), any()))
            .thenReturn(updatedExternalEventState to null)

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).doesNotContain(externalEventRecord)
        verify(checkpoint).externalEventState = updatedExternalEventState
    }

    @Test
    fun `Does not add an external event record when there is no external event state`() {
        whenever(checkpoint.externalEventState).thenReturn(null)

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).doesNotContain(externalEventRecord)
        verify(externalEventManager, never()).getEventToSend(any(), any(), any())
        verify(checkpoint, never()).externalEventState = any()
    }

    @Test
    fun `Raise a fatal error if counterparties cannot be confirmed`() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2, sessionState3))
        whenever(checkpoint.holdingIdentity).thenReturn(HoldingIdentity(ALICE_X500_NAME, ""))

        assertThrows(FlowFatalException::class.java) {
            flowGlobalPostProcessor.postProcess(testContext)
        }
        verify(sessionManager, times(1)).errorSession(any())
        verify(checkpoint, times(1)).putSessionState(any())
    }

    @Test
    fun `Don't check counterparty for sessions already terminated `() {
        sessionState3.apply {
            sessionStartTime = Instant.now().minusSeconds(86400)
            lastReceivedMessageTime = Instant.now().minusSeconds(86400)
            status = SessionStateType.CLOSED
        }

        sessionState2.apply {
            sessionStartTime = Instant.now().minusSeconds(86400)
            lastReceivedMessageTime = Instant.now().minusSeconds(86400)
            status = SessionStateType.ERROR
        }

        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2, sessionState3))
        whenever(checkpoint.holdingIdentity).thenReturn(HoldingIdentity(ALICE_X500_NAME, ""))

        flowGlobalPostProcessor.postProcess(testContext)
        verify(sessionManager, times(0)).errorSession(any())
        verify(checkpoint, times(3)).putSessionState(any())
    }

    @Test
    fun `Don't raise a platform error if counterparties cannot be confirmed within timeout window but the checkpoint is already deleted`() {
        sessionState3.apply {
            sessionStartTime = Instant.now().minusSeconds(86400)
            lastReceivedMessageTime = Instant.now().minusSeconds(86400)
        }

        whenever(checkpoint.doesExist).thenReturn(false)
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2, sessionState3))
        whenever(checkpoint.holdingIdentity).thenReturn(HoldingIdentity(ALICE_X500_NAME, ""))

        assertDoesNotThrow {
            flowGlobalPostProcessor.postProcess(testContext)
        }

        verify(sessionManager, times(1)).errorSession(any())
        verify(checkpoint, times(0)).putSessionState(any())
    }

    @Test
    fun `when open session exists session timeout is set in metadata`() {
        val earliestInstant = Instant.now().minusSeconds(20)
        sessionState1.apply {
            lastReceivedMessageTime = earliestInstant
            status = SessionStateType.CONFIRMED
        }
        sessionState2.apply {
            lastReceivedMessageTime = earliestInstant.plusSeconds(4)
            status = SessionStateType.CONFIRMED
        }
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        val output = flowGlobalPostProcessor.postProcess(testContext)
        val window = Duration.ofMillis(testContext.flowConfig.getLong(SESSION_TIMEOUT_WINDOW))
        val expectedExpiry = (earliestInstant + window).epochSecond
        assertThat(output.metadata).containsEntry(STATE_META_SESSION_EXPIRY_KEY, expectedExpiry)
    }

    @Test
    fun `when open session exists session specific timeout is set in metadata`() {
        val earliestInstant = Instant.now().minusSeconds(20)
        val window = Duration.ofMillis(testContext.flowConfig.getLong(SESSION_TIMEOUT_WINDOW))
        val session1Timeout = window.dividedBy(2)
        val session2Timeout = window.dividedBy(3)
        sessionState1.apply {
            lastReceivedMessageTime = earliestInstant
            status = SessionStateType.CONFIRMED
            sessionProperties = KeyValueStore().apply {
                put(Constants.FLOW_SESSION_TIMEOUT_MS, session1Timeout.toMillis().toString())
            }.avro
        }
        sessionState2.apply {
            lastReceivedMessageTime = earliestInstant.plusSeconds(4)
            status = SessionStateType.CONFIRMED
            sessionProperties = KeyValueStore().apply {
                put(Constants.FLOW_SESSION_TIMEOUT_MS, session2Timeout.toMillis().toString())
            }.avro
        }
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        val output = flowGlobalPostProcessor.postProcess(testContext)
        val expectedExpiry = (earliestInstant + session2Timeout).epochSecond
        assertThat(output.metadata).containsEntry(STATE_META_SESSION_EXPIRY_KEY, expectedExpiry)
    }

    @Test
    fun `when no open session exists and metadata previously had expiry key it is removed`() {
        val earliestInstant = Instant.now().minusSeconds(20)
        sessionState1.apply {
            lastReceivedMessageTime = earliestInstant
            status = SessionStateType.CLOSED
        }
        sessionState2.apply {
            lastReceivedMessageTime = earliestInstant.plusSeconds(4)
            status = SessionStateType.CLOSED
        }
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        val context = testContext.copy(
            metadata = Metadata(mapOf(STATE_META_SESSION_EXPIRY_KEY to earliestInstant.epochSecond))
        )
        val output = flowGlobalPostProcessor.postProcess(context)
        assertThat(output.metadata).doesNotContainKey(STATE_META_SESSION_EXPIRY_KEY)
    }

    @Test
    fun `when open session exists previous metadata key is overwritten`() {
        val earliestInstant = Instant.now().minusSeconds(20)
        sessionState1.apply {
            lastReceivedMessageTime = earliestInstant
            status = SessionStateType.CONFIRMED
        }
        sessionState2.apply {
            lastReceivedMessageTime = earliestInstant.plusSeconds(4)
            status = SessionStateType.CONFIRMED
        }
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1, sessionState2))
        val context = testContext.copy(
            metadata = Metadata(mapOf(STATE_META_SESSION_EXPIRY_KEY to earliestInstant.epochSecond))
        )
        val output = flowGlobalPostProcessor.postProcess(context)
        val window = Duration.ofMillis(testContext.flowConfig.getLong(SESSION_TIMEOUT_WINDOW))
        val expectedExpiry = (earliestInstant + window).epochSecond
        assertThat(output.metadata).containsEntry(STATE_META_SESSION_EXPIRY_KEY, expectedExpiry)
    }
}
