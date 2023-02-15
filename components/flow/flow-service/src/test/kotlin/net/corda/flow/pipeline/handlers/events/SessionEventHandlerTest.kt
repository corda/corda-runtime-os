package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.waiting.sessions.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.impl.FlowProtocol
import net.corda.flow.pipeline.sessions.impl.FlowProtocolStoreImpl
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.SessionManager
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.stream.Stream

@Suppress("MaxLineLength")
class SessionEventHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val CPI_ID = "cpi id"
        const val INITIATING_FLOW_NAME = "Initiating flow"
        const val INITIATED_FLOW_NAME = "Initiated flow"
        private val PROTOCOL = FlowProtocol("protocol", 1)

        @JvmStatic
        fun nonInitSessionEventTypes(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(SessionAck()),
                Arguments.of(SessionData()),
                Arguments.of(SessionClose()),
                Arguments.of(SessionError()),
            )
        }
    }

    private val checkpointSessionState = SessionState()
    private val updatedSessionState = SessionState()
    private val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val flowSandboxService = mock<FlowSandboxService>()
    private val sessionManager = mock<SessionManager>()

    private val holdingIdentity = ALICE_X500_HOLDING_IDENTITY
    private val waitingFor = WaitingFor(WaitingForSessionInit(SESSION_ID))
    private val expectedCheckpoint = mock<FlowCheckpoint>()

    private val fakeCheckpointInitializerService = FakeCheckpointInitializerService(
        waitingFor,
        holdingIdentity.toCorda(),
        expectedCheckpoint
    )

    private val sessionEventHandler =
        SessionEventHandler(flowSandboxService, sessionManager, fakeCheckpointInitializerService)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        checkpointSessionState.sessionId = SESSION_ID
        updatedSessionState.sessionId = SESSION_ID

        whenever(expectedCheckpoint.getSessionState(SESSION_ID)).thenReturn(checkpointSessionState)

        whenever(
            sessionManager.processMessageReceived(
                any(),
                anyOrNull(),
                any(),
                any()
            )
        ).thenReturn(updatedSessionState)

        whenever(flowSandboxService.get(any(), any())).thenReturn(sandboxGroupContext)

        whenever(sandboxGroupContext.protocolStore)
            .thenReturn(
                FlowProtocolStoreImpl(
                    mapOf(INITIATING_FLOW_NAME to listOf(PROTOCOL)),
                    mapOf(PROTOCOL to INITIATED_FLOW_NAME)
                )
            )
    }

    @Test
    fun `Receiving a session init payload creates a checkpoint if one does not exist for the initiated flow and adds the new session to it`() {
        val sessionEvent = createSessionInit()
        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(updatedSessionState)).thenReturn(sessionEvent)

        sessionEventHandler.preProcess(inputContext)
    }

    @Test
    fun `Receiving a session init payload throws an exception when the session manager returns no next received event`() {
        val sessionEvent = createSessionInit()
        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(updatedSessionState)).thenReturn(null)

        assertThrows<FlowEventException> {
            sessionEventHandler.preProcess(inputContext)
        }

        verify(fakeCheckpointInitializerService, never()).initialize(any(), any(), any(), any())
    }

    @Test
    fun `Receiving a session init payload throws an exception if there is no matching initiated flow`() {
        val sessionEvent = createSessionInit()

        whenever(sandboxGroupContext.protocolStore)
            .thenReturn(FlowProtocolStoreImpl(mapOf(), mapOf()))
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(sessionEvent)

        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)
        assertThrows<FlowFatalException> { sessionEventHandler.preProcess(inputContext) }
    }

    @ParameterizedTest(name = "Receiving a {0} payload when a checkpoint does not exist throws an exception")
    @MethodSource("nonInitSessionEventTypes")
    fun `Receiving a non-session init payload when a checkpoint does not exist throws an exception`(payload: Any) {
        val sessionEvent = createSessionEvent(payload)
        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        assertThrows<FlowEventException> {
            sessionEventHandler.preProcess(inputContext)
        }
    }

    private fun createSessionInit(): SessionEvent {
        val payload = SessionInit.newBuilder()
            .setProtocol(PROTOCOL.protocol)
            .setVersions(listOf(1))
            .setFlowId(FLOW_ID)
            .setCpiId(CPI_ID)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .setContextPlatformProperties(emptyKeyValuePairList())
            .setContextUserProperties(emptyKeyValuePairList())
            .build()

        return createSessionEvent(payload)
    }

    private fun createSessionEvent(payload: Any): SessionEvent {
        return SessionEvent.newBuilder()
            .setSessionId(SESSION_ID)
            .setMessageDirection(MessageDirection.INBOUND)
            .setTimestamp(Instant.now())
            .setSequenceNum(1)
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setPayload(payload)
            .setInitiatedIdentity(ALICE_X500_HOLDING_IDENTITY)
            .setInitiatingIdentity(BOB_X500_HOLDING_IDENTITY)
            .build()
    }

    private class FakeCheckpointInitializerService(
        val waitingForExpected: WaitingFor,
        val holdingIdentityExpected: HoldingIdentity,
        val checkpointExpected: FlowCheckpoint
    ) : CheckpointInitializer {

        override fun initialize(
            checkpoint: FlowCheckpoint,
            waitingFor: WaitingFor,
            holdingIdentity: HoldingIdentity,
            contextBuilder: (Set<SecureHash>) -> FlowStartContext
        ) {
            val startContext = contextBuilder(emptySet())
            assertThat(checkpoint).isEqualTo(checkpointExpected)
            assertThat(waitingFor.value).isEqualTo(waitingForExpected.value)
            assertThat(holdingIdentity).isEqualTo(holdingIdentityExpected)

            assertThat(startContext.statusKey).isEqualTo(FlowKey(SESSION_ID, ALICE_X500_HOLDING_IDENTITY))
            assertThat(startContext.initiatorType).isEqualTo(FlowInitiatorType.P2P)
            assertThat(startContext.requestId).isEqualTo(SESSION_ID)
            assertThat(startContext.identity).isEqualTo(ALICE_X500_HOLDING_IDENTITY)
            assertThat(startContext.cpiId).isEqualTo(CPI_ID)
            assertThat(startContext.initiatedBy).isEqualTo(BOB_X500_HOLDING_IDENTITY)
            assertThat(startContext.flowClassName).isEqualTo(INITIATED_FLOW_NAME)
        }
    }
}

