package net.corda.flow.pipeline.handlers.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.ALICE_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.data.flow.state.waiting.WaitingForStartFlow
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.impl.FlowProtocol
import net.corda.flow.pipeline.sessions.impl.FlowProtocolStoreImpl
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_REQUIRE_CLOSE
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_TIMEOUT_MS
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
import org.mockito.kotlin.times
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
    private val flowSessionManager = mock<FlowSessionManager>()

    private val holdingIdentity = ALICE_X500_HOLDING_IDENTITY
    private val waitingFor = WaitingFor(WaitingForStartFlow)
    private val expectedCheckpoint = mock<FlowCheckpoint>()

    private val fakeCheckpointInitializerService = FakeCheckpointInitializerService(
        waitingFor,
        holdingIdentity.toCorda(),
        expectedCheckpoint
    )

    private val sessionEventHandler =
        SessionEventHandler(flowSandboxService, sessionManager, fakeCheckpointInitializerService, flowSessionManager)

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
                    mapOf(PROTOCOL to INITIATING_FLOW_NAME),
                    mapOf(PROTOCOL to INITIATED_FLOW_NAME)
                )
            )
    }

    @Test
    fun `Receiving a counterparty message creates a checkpoint if one does not exist for the initiated flow and adds the new session to it`() {
        val sessionEvent = createCounterpartyRequest()
        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(updatedSessionState)).thenReturn(sessionEvent)

        sessionEventHandler.preProcess(inputContext)

        verify(sessionManager, times(1)).generateSessionState(any(), any(), any(), any(), any())
    }

    @Test
    fun `Receiving a session data with init payload creates a checkpoint and adds the new session to it, does not reply with confirm`() {
        val sessionEvent = createSessionDataWithInit()
        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)

        whenever(sessionManager.getNextReceivedEvent(updatedSessionState)).thenReturn(sessionEvent)

        sessionEventHandler.preProcess(inputContext)

        verify(sessionManager, times(1)).generateSessionState(any(), any(), any(), any(), any())
        verify(flowSessionManager, times(0)).sendCounterpartyInfoResponse(any(), any(), anyOrNull(), any())
    }

    @Test
    fun `Receiving a counterparty request payload sends an error message if there is no matching initiated flow`() {
        val sessionEvent = createCounterpartyRequest()
        val inputContext = buildFlowEventContext(checkpoint = expectedCheckpoint, inputEventPayload = sessionEvent)

        whenever(sandboxGroupContext.protocolStore)
            .thenReturn(FlowProtocolStoreImpl(mapOf(), mapOf(), mapOf()))
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(sessionEvent)

        val sessionEventHandler = SessionEventHandler(
            flowSandboxService, sessionManager, fakeCheckpointInitializerService, flowSessionManager)

        sessionEventHandler.preProcess(inputContext)
        verify(sessionManager, times(1)).generateSessionState(any(), any(), anyOrNull(), any(), any())
        verify(flowSessionManager, times(1)).sendErrorMessages(any(), any(), anyOrNull(), any())
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

    private fun createCounterpartyRequest(): SessionEvent {
        val payload = SessionInit.newBuilder()
            .setFlowId(FLOW_ID)
            .setCpiId(CPI_ID)
            .setContextPlatformProperties(emptyKeyValuePairList())
            .setContextUserProperties(emptyKeyValuePairList())
            .build()

        return createSessionEvent(SessionCounterpartyInfoRequest(payload))
    }

    private fun createSessionDataWithInit(): SessionEvent {
        val sessionInit = SessionInit.newBuilder()
            .setFlowId(FLOW_ID)
            .setCpiId(CPI_ID)
            .setContextPlatformProperties(emptyKeyValuePairList())
            .setContextUserProperties(emptyKeyValuePairList())
            .build()

        val payload = SessionData(ByteBuffer.allocate(1), sessionInit)

        return createSessionEvent(payload)
    }

    private fun contextSessionProperties() :KeyValuePairList {
        return KeyValueStore().apply {
            put(FLOW_PROTOCOL, PROTOCOL.protocol)
            put(FLOW_PROTOCOL_VERSIONS_SUPPORTED, "1")
            put(FLOW_SESSION_REQUIRE_CLOSE, "true")
            put(FLOW_SESSION_TIMEOUT_MS, "1800000")
        }.avro
    }
    private fun createSessionEvent(payload: Any): SessionEvent {
        return SessionEvent.newBuilder()
            .setSessionId(SESSION_ID)
            .setMessageDirection(MessageDirection.INBOUND)
            .setTimestamp(Instant.now())
            .setSequenceNum(1)
            .setPayload(payload)
            .setInitiatedIdentity(ALICE_X500_HOLDING_IDENTITY)
            .setInitiatingIdentity(BOB_X500_HOLDING_IDENTITY)
            .setContextSessionProperties(contextSessionProperties())
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
        }
    }
}