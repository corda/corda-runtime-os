package net.corda.flow.pipeline.handlers.requests.sessions.service

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.flow.utils.mutableKeyValuePairList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GenerateSessionServiceTest {

    private val sessionId1 = "s1"
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val testContext = RequestHandlerTestContext(Any())

    private val sessionInfo = setOf(SessionInfo(sessionId1, ALICE_X500_NAME))
    private val generateSessionService = GenerateSessionService(
        testContext.flowSessionManager,
        testContext.flowSandboxService
    )
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val protocolStore = mock<FlowProtocolStore>()

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        whenever(
            testContext.flowSessionManager.generateSessionState(
                eq(testContext.flowCheckpoint),
                eq(sessionId1),
                eq(ALICE_X500_NAME),
                any(),
                any(),
            )
        ).thenReturn(sessionState1)
        whenever(testContext.flowSandboxService.get(any(), any())).thenReturn(sandboxGroupContext)
        whenever(sandboxGroupContext.protocolStore).thenReturn(protocolStore)
        whenever(protocolStore.protocolsForInitiator(any(), any())).thenReturn(Pair("protocol", listOf(1)))
        whenever(testContext.flowSessionManager.sendCounterpartyInfoRequest(any(), any(), any(), any(), any(), any()))
            .thenReturn(sessionState1)
        whenever(testContext.flowCheckpoint.getSessionState(sessionId1)).thenReturn(null)
        whenever(testContext.flowCheckpoint.getSessionState(sessionId1)).thenReturn(null)
        whenever(testContext.flowStack.nearestFirst(any())).thenReturn(
            FlowStackItem(
                "flow",
                true,
                mutableListOf(),
                mutableKeyValuePairList(),
                mutableKeyValuePairList()
            )
        )
    }

    @Test
    fun `Verify no sessions required to be initiated`() {
        whenever(testContext.flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        val sessions = generateSessionService.getSessionsNotGenerated(testContext.flowEventContext, sessionInfo)
        assertThat(sessions).isEmpty()
    }

    @Test
    fun `Verify sessions found to be initiated`() {
        val sessions = generateSessionService.getSessionsNotGenerated(testContext.flowEventContext, sessionInfo)
        assertThat(sessions.size).isEqualTo(1)
        assertThat(sessions.first()).isEqualTo(sessionInfo.first())
    }

    @Test
    fun `Session counterpartyInfoRequest event sent to session manager and checkpoint updated with session state`() {
        generateSessionService.generateSessions(testContext.flowEventContext, sessionInfo, true)
        verify(testContext.flowCheckpoint, times(2)).putSessionState(any())
        verify(testContext.flowSessionManager).generateSessionState(any(), any(), any(), any(), any())
    }

    @Test
    fun `No counterpartyInfoRequest event sent to session manager and checkpoint updated with session state`() {
        generateSessionService.generateSessions(testContext.flowEventContext, sessionInfo, false)
        verify(testContext.flowCheckpoint, times(1)).putSessionState(any())
        verify(testContext.flowSessionManager).generateSessionState(any(), any(), any(), any(), any())
    }

    @Test
    fun `No initiating flow in the subflow stack throws platform exception`() {
        whenever(testContext.flowStack.nearestFirst(any())).thenReturn(null)
        assertThrows<FlowPlatformException> {
            generateSessionService.generateSessions(testContext.flowEventContext, sessionInfo)
        }
    }

    @Test
    fun `No flows in the subflow stack throws fatal exception`() {
        whenever(testContext.flowStack.isEmpty()).thenReturn(true)
        assertThrows<FlowFatalException> {
            generateSessionService.generateSessions(testContext.flowEventContext, sessionInfo)
        }
    }

    @Test
    fun `Does not add an output record`() {
        generateSessionService.generateSessions(testContext.flowEventContext, sessionInfo)
        assertThat(testContext.flowEventContext.outputRecords).hasSize(0)
    }
}
