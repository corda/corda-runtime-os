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
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.waiting.sessions.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.session.manager.SessionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
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
    private val checkpoint = mock<FlowCheckpoint>()
    private val sandboxGroupContext = mock<SandboxGroupContext>()
    private val flowSandboxService = mock<FlowSandboxService>()
    private val sessionManager = mock<SessionManager>()

    private val sessionEventHandler = SessionEventHandler(flowSandboxService, sessionManager)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        checkpointSessionState.sessionId = SESSION_ID
        updatedSessionState.sessionId = SESSION_ID

        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(checkpointSessionState)

        whenever(
            sessionManager.processMessageReceived(
                any(),
                anyOrNull(),
                any(),
                any()
            )
        ).thenReturn(updatedSessionState)

        whenever(flowSandboxService.get(any())).thenReturn(sandboxGroupContext)

        whenever(sandboxGroupContext.get(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS, Map::class.java))
            .thenReturn(mapOf(Pair(CPI_ID, INITIATING_FLOW_NAME) to INITIATED_FLOW_NAME))
    }

    @Test
    fun `Receiving a session init payload creates a checkpoint if one does not exist for the initiated flow and adds the new session to it`() {
        val sessionEvent = createSessionInit()
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(updatedSessionState)).thenReturn(sessionEvent)

        sessionEventHandler.preProcess(inputContext)

        val expectedStartFlowContext: (FlowStartContext) -> Boolean = { context ->
            assertThat(context.statusKey).isEqualTo(FlowKey(SESSION_ID, ALICE_X500_HOLDING_IDENTITY))
            assertThat(context.initiatorType).isEqualTo(FlowInitiatorType.P2P)
            assertThat(context.requestId).isEqualTo(SESSION_ID)
            assertThat(context.identity).isEqualTo(ALICE_X500_HOLDING_IDENTITY)
            assertThat(context.cpiId).isEqualTo(CPI_ID)
            assertThat(context.initiatedBy).isEqualTo(BOB_X500_HOLDING_IDENTITY)
            assertThat(context.flowClassName).isEqualTo(INITIATED_FLOW_NAME)
            true
        }

        val expectedSessionInit: (WaitingFor) -> Boolean = { waitingFor ->
            val sessionInit = waitingFor.value
            sessionInit is WaitingForSessionInit && sessionInit.sessionId == SESSION_ID
        }

        verify(checkpoint).initFromNew(
            eq(FLOW_ID),
            argThat { fsc -> expectedStartFlowContext(fsc) },
            argThat { wf-> expectedSessionInit(wf) }
        )
    }

    @Test
    fun `Receiving a session init payload does not create a checkpoint when the session manager returns no next received event`() {
        val sessionEvent = createSessionInit()
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(updatedSessionState)).thenReturn(null)

        sessionEventHandler.preProcess(inputContext)

        verify(checkpoint, never()).initFromNew(any(), any(), any())
    }

    @Test
    fun `Receiving a session init payload throws an exception if there is no matching initiated flow`() {
        val sessionEvent = createSessionInit()

        whenever(sandboxGroupContext.get(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS, Map::class.java))
            .thenReturn(emptyMap<String, String>())
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(sessionEvent)

        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        assertThrows<FlowProcessingException> { sessionEventHandler.preProcess(inputContext) }
    }

    @ParameterizedTest(name = "Receiving a {0} payload updates the existing checkpoint")
    @MethodSource("nonInitSessionEventTypes")
    fun `Receiving a session data payload does not create a checkpoint`(payload: Any) {
        val sessionEvent = createSessionEvent(payload)
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        sessionEventHandler.preProcess(inputContext)

        verify(checkpoint).putSessionState(updatedSessionState)
    }

    private fun createSessionInit(): SessionEvent {
        val payload = SessionInit.newBuilder()
            .setFlowName(INITIATING_FLOW_NAME)
            .setFlowId(FLOW_ID)
            .setCpiId(CPI_ID)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
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
}