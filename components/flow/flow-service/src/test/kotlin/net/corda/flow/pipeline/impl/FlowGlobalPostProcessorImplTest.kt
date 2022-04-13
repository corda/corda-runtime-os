package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.pipeline.factory.RecordFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.session.manager.SessionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowGlobalPostProcessorImplTest {

    private val sessionId1 = "s1"
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionEvent1 = SessionEvent().apply {
        this.sessionId = sessionId1
        this.sequenceNum = 1
    }
    private val sessionEvent2 = SessionEvent().apply {
        this.sessionId = sessionId1
        this.sequenceNum = 2
    }
    private val record1 = Record("t", "1", FlowMapperEvent(Any()))
    private val record2 = Record("t", "2", FlowMapperEvent(Any()))
    private val sessionManager = mock<SessionManager>()
    private val recordFactory = mock<RecordFactory>()
    private val checkpoint = mock<FlowCheckpoint>()
    private val testContext = buildFlowEventContext(checkpoint, Any())
    private val flowGlobalPostProcessor = FlowGlobalPostProcessorImpl(sessionManager, recordFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState1))
        whenever(checkpoint.flowKey).thenReturn(FlowKey("flow id", ALICE_X500_HOLDING_IDENTITY))
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(
            sessionManager.getMessagesToSend(
                eq(sessionState1),
                any(),
                eq(testContext.config),
                eq(ALICE_X500_HOLDING_IDENTITY)
            )
        )
            .thenReturn(sessionState1 to listOf(sessionEvent1, sessionEvent2))

        whenever(recordFactory.createFlowMapperSessionEventRecord(sessionEvent1)).thenReturn(record1)
        whenever(recordFactory.createFlowMapperSessionEventRecord(sessionEvent2)).thenReturn(record2)
    }

    @Test
    fun `Adds output records containing session events to send to peers`() {
        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertThat(outputContext.outputRecords).containsOnly(record1, record2)
    }

    @Test
    fun `Updates session states`() {
        flowGlobalPostProcessor.postProcess(testContext)

        verify(checkpoint).putSessionState(sessionState1)
    }

    @Test
    fun `Does nothing when there is no checkpoint`() {
        whenever(checkpoint.sessions).thenReturn(emptyList())

        val outputContext = flowGlobalPostProcessor.postProcess(testContext)

        assertEquals(testContext, outputContext)
    }
}