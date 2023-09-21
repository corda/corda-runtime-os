package net.corda.flow.pipeline.sessions.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.mutableKeyValuePairList
import net.corda.messaging.api.records.Record
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.session.manager.SessionManager
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.stream.Stream

class FlowSessionManagerImplTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        const val CPI_ID = "cpi id"
        const val INITIATING_FLOW_NAME = "Initiating flow"
        private const val PROTOCOL = "protocol"
        val X500_NAME = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")
        val HOLDING_IDENTITY = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group id")
        val COUNTERPARTY_HOLDING_IDENTITY = HoldingIdentity(X500_NAME.toString(), "group id")

        val sessionContext = KeyValueStore().apply {
            this[FLOW_PROTOCOL] = PROTOCOL
            this[FLOW_PROTOCOL_VERSIONS_SUPPORTED] = "1"
        }

        @JvmStatic
        fun sendingSessionStateTypes(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(SessionStateType.CONFIRMED, true),
                Arguments.of(SessionStateType.CREATED, true),
                Arguments.of(SessionStateType.CLOSING, false),
                Arguments.of(SessionStateType.CLOSED, false),
                Arguments.of(SessionStateType.ERROR, false)
            )
        }

        @JvmStatic
        fun receivingSessionStateTypes(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(SessionStateType.CONFIRMED, true),
                Arguments.of(SessionStateType.CREATED, true),
                Arguments.of(SessionStateType.CLOSING, true),
                Arguments.of(SessionStateType.CLOSED, false),
                Arguments.of(SessionStateType.ERROR, false)
            )
        }
    }

    private val sessionState = buildSessionState(
        SessionStateType.CONFIRMED,
        0,
        mutableListOf(),
        0,
        mutableListOf(),
        sessionId = SESSION_ID,
        counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
        requireClose = false
    )

    private val anotherSessionState = buildSessionState(
        SessionStateType.CONFIRMED,
        0,
        mutableListOf(),
        0,
        mutableListOf(),
        sessionId = ANOTHER_SESSION_ID,
        counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
        requireClose = false
    )

    private val flowKey = mock<FlowKey>()
    private val errorSessionState1 = mock<SessionState> { whenever(it.status).thenReturn(SessionStateType.ERROR) }
    private val errorSessionState2 = mock<SessionState> { whenever(it.status).thenReturn(SessionStateType.ERROR) }
    private val errorEvent1 = mock<SessionEvent> { whenever(it.sessionId).thenReturn("s1") }
    private val errorEvent2 = mock<SessionEvent> { whenever(it.sessionId).thenReturn("s2") }
    private val errorPairing1 = errorSessionState1 to listOf(errorEvent1)
    private val errorPairing2 = errorSessionState2 to listOf(errorEvent2)
    private val sessionsWithErrors = listOf(
        errorSessionState1,
        errorSessionState2,
        mock { it.status = SessionStateType.CLOSED },
        mock { it.status = SessionStateType.CLOSING },
        mock { it.status = SessionStateType.CONFIRMED },
        mock { it.status = SessionStateType.CREATED },
    )
    private val record1 = Record(topic = "topic", key = "s1", value = FlowMapperEvent("payload"))
    private val record2 = Record(topic = "topic", key = "s2", value = FlowMapperEvent("payload"))

    private val sessionManager = mock<SessionManager>()
    private val checkpoint = mock<FlowCheckpoint>()
    private val flowStack = mock<FlowStack>()
    private val flowStartContext = mock<FlowStartContext>()
    private val flowRecordFactory = mock<FlowRecordFactory>()

    private val flowSessionManager = FlowSessionManagerImpl(sessionManager, flowRecordFactory)

    @BeforeEach
    fun setup() {
        whenever(checkpoint.flowId).thenReturn(FLOW_ID)
        whenever(checkpoint.flowKey).thenReturn(flowKey)
        whenever(checkpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(flowStartContext.cpiId).thenReturn("CPIID")
        whenever(flowKey.identity).thenReturn(HOLDING_IDENTITY)
        whenever(checkpoint.holdingIdentity).thenReturn(HOLDING_IDENTITY.toCorda())
        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(sessionState)
        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(anotherSessionState)
    }

    @Test
    fun `get session error event records`() {
        whenever(checkpoint.sessions).thenReturn(sessionsWithErrors)

        whenever(
            sessionManager.getMessagesToSend(
                eq(errorSessionState1),
                any(),
                any(),
                any()
            )
        ).thenReturn(errorPairing1)
        whenever(
            sessionManager.getMessagesToSend(
                eq(errorSessionState2),
                any(),
                any(),
                any()
            )
        ).thenReturn(errorPairing2)

        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s1"), eq(errorEvent1))).thenReturn(record1)
        whenever(flowRecordFactory.createFlowMapperEventRecord(eq("s2"), eq(errorEvent2))).thenReturn(record2)

        val result = flowSessionManager.getSessionErrorEventRecords(checkpoint, mock(), Instant.now())

        assertThat(result).isEqualTo(listOf(record1, record2))
    }

    @Test
    fun `send counterpartyRequest creates a SessionCounterpartyInfoRequest message and processes it`() {
        whenever(sessionManager.processMessageToSend(any(), any(), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        whenever(flowStack.peek()).thenReturn(
            FlowStackItem.newBuilder().setFlowName(INITIATING_FLOW_NAME).setIsInitiatingFlow(true)
                .setSessions(emptyList()).setContextPlatformProperties(mutableKeyValuePairList())
                .setContextUserProperties(
                    mutableKeyValuePairList()
                ).build()
        )
        whenever(checkpoint.flowStartContext).thenReturn(FlowStartContext().apply {
            cpiId = CPI_ID
        })
        whenever(checkpoint.flowStack).thenReturn(flowStack)

        val instant = Instant.now()

        val userContext = KeyValueStore().apply {
            this["user"] = "user"
        }
        val platformContext = KeyValueStore().apply {
            this["platform"] = "platform"
        }

        val expectedSessionInit = SessionInit.newBuilder()
            .setFlowId(FLOW_ID)
            .setCpiId(CPI_ID)
            .setContextPlatformProperties(platformContext.avro)
            .setContextUserProperties(userContext.avro)
            .build()
        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionCounterpartyInfoRequest(expectedSessionInit),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val sessionState = flowSessionManager.sendCounterpartyInfoRequest(
            checkpoint,
            SESSION_ID,
            userContext.avro,
            platformContext.avro,
            X500_NAME,
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), any(), any(), eq(instant), any())
        assertEquals(expectedSessionEvent, sessionState.sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `sendDataMessages creates SessionData messages and processes them`() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))

        whenever(sessionManager.processMessageToSend(any(), eq(sessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }
        whenever(sessionManager.processMessageToSend(any(), eq(anotherSessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    anotherSessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        val instant = Instant.now()

        val payload = byteArrayOf(1)
        val anotherPayload = byteArrayOf(2)

        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionData(ByteBuffer.wrap(payload), null),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )
        val anotherExpectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            ANOTHER_SESSION_ID,
            sequenceNum = null,
            payload = SessionData(ByteBuffer.wrap(anotherPayload), null),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val sessionStates = flowSessionManager.sendDataMessages(
            checkpoint,
            mapOf(SessionInfo(SESSION_ID, X500_NAME) to payload, SessionInfo(ANOTHER_SESSION_ID, X500_NAME) to anotherPayload),
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(sessionState), any(), eq(instant), any())
        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(anotherSessionState), any(), eq(instant), any())
        assertEquals(expectedSessionEvent, sessionStates[0].sendEventsState.undeliveredMessages.single())
        assertEquals(anotherExpectedSessionEvent, sessionStates[1].sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `sendDataMessages does nothing when there are no sessions passed in`() {
        val instant = Instant.now()
        flowSessionManager.sendDataMessages(checkpoint, emptyMap(), instant)
        verify(sessionManager, never()).processMessageToSend(eq(FLOW_ID), any(), any(), eq(instant), any())
    }

    @Test
    fun `sendDataMessages throws an error when the checkpoint does not contain a passed in session`() {
        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)

        val instant = Instant.now()

        assertThrows<FlowSessionStateException> {
            flowSessionManager.sendDataMessages(
                checkpoint,
                mapOf(SessionInfo(SESSION_ID, X500_NAME) to byteArrayOf(), SessionInfo(ANOTHER_SESSION_ID, X500_NAME) to byteArrayOf()),
                instant
            )
        }
    }

    @Test
    fun `sendCloseMessages creates SessionClose messages and processes them`() {
        whenever(sessionManager.processMessageToSend(any(), eq(sessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }
        whenever(sessionManager.processMessageToSend(any(), eq(anotherSessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    anotherSessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        val instant = Instant.now()

        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionClose(),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )
        val anotherExpectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            ANOTHER_SESSION_ID,
            sequenceNum = null,
            payload = SessionClose(),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val sessionStates = flowSessionManager.sendCloseMessages(
            checkpoint,
            listOf(SESSION_ID, ANOTHER_SESSION_ID),
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(sessionState), any(), eq(instant), any())
        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(anotherSessionState), any(), eq(instant), any())
        assertEquals(expectedSessionEvent, sessionStates[0].sendEventsState.undeliveredMessages.single())
        assertEquals(anotherExpectedSessionEvent, sessionStates[1].sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `generateSessionState calls session manager to generate a session state`() {
        flowSessionManager.generateSessionState(checkpoint, SESSION_ID, X500_NAME, emptyKeyValuePairList(), Instant.now())
        verify(sessionManager).generateSessionState(any(), any(), any(), any(), any())
    }

    @Test
    fun `sendCloseMessages does nothing when there are no sessions passed in`() {
        val instant = Instant.now()
        flowSessionManager.sendCloseMessages(checkpoint, emptyList(), instant)
        verify(sessionManager, never()).processMessageToSend(eq(FLOW_ID), any(), any(), eq(instant), any())
    }

    @Test
    fun `sendCloseMessages throws an error when the checkpoint does not contain a passed in session`() {
        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)

        val instant = Instant.now()

        assertThrows<FlowSessionStateException> {
            flowSessionManager.sendCloseMessages(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                instant
            )
        }
    }

    @Test
    fun `getReceivedEvents returns events for the passed in sessions`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = Unit,
            contextSessionProps = null
        )
        val anotherSessionEvent =
            buildSessionEvent(
                MessageDirection.OUTBOUND,
                ANOTHER_SESSION_ID,
                sequenceNum = null,
                payload = Unit,
                contextSessionProps = null
            )

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(anotherSessionEvent)

        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))

        assertEquals(listOf(sessionState to sessionEvent, anotherSessionState to anotherSessionEvent), receivedEvents)
    }

    @Test
    fun `getReceivedEvents does not return state event pairs when there is no next event`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = Unit,
            contextSessionProps = null
        )

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(null)

        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))

        assertEquals(listOf(sessionState to sessionEvent), receivedEvents)
    }

    @Test
    fun `getReceivedEvents does nothing when no sessions are passed in`() {
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))

        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, emptyList())

        assertEquals(emptyList<Pair<SessionState, SessionEvent>>(), receivedEvents)
        verify(sessionManager, never()).getNextReceivedEvent(any())
    }

    @Test
    fun `getReceivedEvents throws an error when the checkpoint does not contain a passed in session`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = Unit,
            contextSessionProps = sessionContext.avro
        )

        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)

        assertThrows<FlowSessionStateException> {
            flowSessionManager.getReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))
        }
    }

    @Test
    fun `acknowledgeReceivedEvents acknowledges the passed in events`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = 1,
            payload = Unit,
            contextSessionProps = null
        )
        val anotherSessionEvent =
            buildSessionEvent(
                MessageDirection.OUTBOUND,
                ANOTHER_SESSION_ID,
                sequenceNum = 2,
                payload = Unit,
                contextSessionProps = null
            )
        flowSessionManager.acknowledgeReceivedEvents(
            listOf(
                sessionState to sessionEvent,
                anotherSessionState to anotherSessionEvent
            )
        )
        verify(sessionManager).acknowledgeReceivedEvent(sessionState, 1)
        verify(sessionManager).acknowledgeReceivedEvent(anotherSessionState, 2)
    }

    @Test
    fun `acknowledgeReceivedEvents does nothing when no events are passed in`() {
        flowSessionManager.acknowledgeReceivedEvents(emptyList())
        verify(sessionManager, never()).acknowledgeReceivedEvent(any(), any())
    }

    @Test
    fun `hasReceivedEvents returns true if an event for every passed in session has been received`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = Unit,
            contextSessionProps = null
        )
        val anotherSessionEvent =
            buildSessionEvent(
                MessageDirection.OUTBOUND,
                ANOTHER_SESSION_ID,
                sequenceNum = null,
                payload = Unit,
                contextSessionProps = null
            )

        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(anotherSessionEvent)

        assertTrue(flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID)))
    }

    @Test
    fun `hasReceivedEvents returns false if any event for the passed in sessions has not been received`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = Unit,
            contextSessionProps = sessionContext.avro
        )

        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))
        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(sessionEvent)
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(null)

        assertFalse(flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID)))
    }

    @Test
    fun `hasReceivedEvents returns true if no sessions were passed in`() {
        assertTrue(flowSessionManager.hasReceivedEvents(checkpoint, listOf()))
    }

    @Test
    fun `getSessionsWithStatus returns sessions that have the passed in status`() {
        sessionState.status = SessionStateType.CLOSED
        anotherSessionState.status = SessionStateType.CONFIRMED

        assertEquals(
            listOf(sessionState),
            flowSessionManager.getSessionsWithStatus(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                SessionStateType.CLOSED
            )
        )
    }

    @Test
    fun `getSessionsWithStatus returns an empty list if no sessions have the passed in status`() {
        sessionState.status = SessionStateType.CONFIRMED
        anotherSessionState.status = SessionStateType.CONFIRMED

        assertEquals(
            emptyList<SessionState>(),
            flowSessionManager.getSessionsWithStatus(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                SessionStateType.CLOSED
            )
        )
    }

    @Test
    fun `getSessionsWithStatus throws an exception if a session does not exist`() {
        sessionState.status = SessionStateType.CLOSED

        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)

        assertThrows<FlowSessionStateException> {
            flowSessionManager.getSessionsWithStatus(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                SessionStateType.CLOSED
            )
        }
    }

    @Test
    fun `getSessionsWithStatus returns an empty list if no sessions`() {
        assertEquals(
            emptyList<SessionState>(),
            flowSessionManager.getSessionsWithStatus(
                checkpoint,
                emptyList(),
                SessionStateType.CLOSED
            )
        )
    }

    @Test
    fun `doAllSessionsHaveStatusIn returns true if all sessions have the passed in statuses`() {
        sessionState.status = SessionStateType.CONFIRMED
        anotherSessionState.status = SessionStateType.CLOSING

        assertTrue(
            flowSessionManager.doAllSessionsHaveStatusIn(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSING, SessionStateType.CONFIRMED)
            )
        )
    }

    @Test
    fun `doAllSessionsHaveStatusIn returns false if any session does not have the passed in statuses`() {
        sessionState.status = SessionStateType.CLOSED
        anotherSessionState.status = SessionStateType.CONFIRMED

        assertFalse(
            flowSessionManager.doAllSessionsHaveStatusIn(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED)
            )
        )
    }

    @Test
    fun `doAllSessionsHaveStatusIn returns false if none of the sessions have the passed in statuses`() {
        sessionState.status = SessionStateType.CLOSING
        anotherSessionState.status = SessionStateType.CONFIRMED

        assertFalse(
            flowSessionManager.doAllSessionsHaveStatusIn(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED)
            )
        )
    }

    @Test
    fun `doAllSessionsHaveStatusIn throws an exception if a session does not exist`() {
        sessionState.status = SessionStateType.CLOSING

        whenever(checkpoint.getSessionState(ANOTHER_SESSION_ID)).thenReturn(null)

        assertThrows<FlowSessionStateException> {
            flowSessionManager.doAllSessionsHaveStatusIn(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID),
                listOf(SessionStateType.CLOSED)
            )
        }
    }

    @Test
    fun `doAllSessionsHaveStatusIn returns true there are no sessions`() {
        assertTrue(
            flowSessionManager.doAllSessionsHaveStatusIn(
                checkpoint,
                emptyList(),
                listOf(SessionStateType.CLOSED)
            )
        )
    }

    @ParameterizedTest(name = "validate session states when sending - throws if not CONFIRMED status={0}")
    @MethodSource("sendingSessionStateTypes")
    fun `validate session states when sending - throws if not CONFIRMED`(
        status: SessionStateType,
        expectedResult: Boolean
    ) {
        sessionState.status = status
        anotherSessionState.status = SessionStateType.CONFIRMED

        if (expectedResult) {
            flowSessionManager.sendDataMessages(
                checkpoint,
                setOf(SessionInfo(SESSION_ID, X500_NAME), SessionInfo(ANOTHER_SESSION_ID, X500_NAME)).associateWith { byteArrayOf() },
                Instant.now()
            )
        } else {
            assertThrows<FlowSessionStateException> {
                flowSessionManager.sendDataMessages(
                    checkpoint,
                    setOf(SessionInfo(SESSION_ID, X500_NAME), SessionInfo(ANOTHER_SESSION_ID, X500_NAME)).associateWith { byteArrayOf() },
                    Instant.now()
                )
            }
        }
    }

    @ParameterizedTest(
        name = "validate session states when receiving - throws if not CONFIRMED or CLOSING status={0}"
    )
    @MethodSource("receivingSessionStateTypes")
    fun `validate session states when receiving - throws if not CONFIRMED or CLOSING`(
        status: SessionStateType,
        expectedResult: Boolean
    ) {
        sessionState.status = status
        anotherSessionState.status = SessionStateType.CONFIRMED

        if (expectedResult) {
            flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))
        } else {
            assertThrows<FlowSessionStateException> {
                flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID))
            }
        }
    }

    @Test
    fun `validate session states - always throws if one of the sessions is missing`() {
        sessionState.status = SessionStateType.ERROR
        anotherSessionState.status = SessionStateType.CLOSED

        val sendError = assertThrows<FlowSessionStateException> {
            flowSessionManager.sendDataMessages(
                checkpoint,
                setOf(SessionInfo(SESSION_ID, X500_NAME), SessionInfo(ANOTHER_SESSION_ID, X500_NAME)).associateWith { byteArrayOf() },
                Instant.now()
            )
        }

        assertThat(sendError.message).isEqualTo("2 of 2 sessions are invalid ['${SESSION_ID}'=ERROR, '${ANOTHER_SESSION_ID}'=CLOSED]")

        val receiveError = assertThrows<FlowSessionStateException> {
            flowSessionManager.hasReceivedEvents(checkpoint, listOf(SESSION_ID, ANOTHER_SESSION_ID))
        }

        assertThat(receiveError.message).isEqualTo("2 of 2 sessions are invalid ['${SESSION_ID}'=ERROR, '${ANOTHER_SESSION_ID}'=CLOSED]")
    }

    @Test
    fun `validate session states - exception message lists all failures`() {
        sessionState.status = SessionStateType.CONFIRMED
        val error = assertThrows<FlowSessionStateException> {
            flowSessionManager.sendDataMessages(
                checkpoint,
                setOf(SessionInfo(SESSION_ID, X500_NAME), SessionInfo("unknown session id", X500_NAME)).associateWith { byteArrayOf() },
                Instant.now()
            )
        }

        assertThat(error.message).isEqualTo("1 of 2 sessions are invalid ['unknown session id'=MISSING]")
    }

    @Test
    fun `validate next message is close success`() {
        val closingSessionState = buildSessionState(
            SessionStateType.CLOSING,
            1,
            mutableListOf(buildSessionEvent(
                MessageDirection.INBOUND,
                SESSION_ID,
                1,
                SessionClose(),
                contextSessionProps = sessionContext.avro
            )),
            0,
            mutableListOf(),
            sessionId = SESSION_ID,
            counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            requireClose = false
        )

        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(closingSessionState)

        val closingList = flowSessionManager.getSessionsWithNextMessageClose(checkpoint, listOf(SESSION_ID))
        assertThat(closingList).isNotEmpty
        assertThat(closingList.first().sessionId).isEqualTo(SESSION_ID)
    }

    @Test
    fun `validate doesn't find closing state when closing session not listed in given session ids`() {
        val closingSessionState = buildSessionState(
            SessionStateType.CLOSING,
            1,
            mutableListOf(buildSessionEvent(
                MessageDirection.INBOUND,
                SESSION_ID,
                1,
                SessionClose(),
                contextSessionProps = sessionContext.avro
            )),
            0,
            mutableListOf(),
            sessionId = SESSION_ID,
            counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            requireClose = false
        )

        whenever(checkpoint.sessions).thenReturn(listOf(closingSessionState))

        val closingList = flowSessionManager.getSessionsWithNextMessageClose(checkpoint, listOf(ANOTHER_SESSION_ID))
        assertThat(closingList).isEmpty()
    }

    @Test
    fun `validate next message is not close success`() {
        val closingSessionState = buildSessionState(
            SessionStateType.CLOSING,
            2,
            mutableListOf(
                buildSessionEvent(MessageDirection.INBOUND, SESSION_ID, 1, SessionData(), contextSessionProps = sessionContext.avro),
                buildSessionEvent(MessageDirection.INBOUND, SESSION_ID, 2, SessionClose(), contextSessionProps = sessionContext.avro),
            ),
            0,
            mutableListOf(),
            sessionId = SESSION_ID,
            counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            requireClose = false
        )

        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(closingSessionState)

        val closingList = flowSessionManager.getSessionsWithNextMessageClose(checkpoint, listOf(SESSION_ID))
        assertThat(closingList).isEmpty()
    }

    @Test
    fun `validate out of order close`() {
        val closingSessionState = buildSessionState(
            SessionStateType.CLOSING,
            0,
            mutableListOf(
                buildSessionEvent(MessageDirection.INBOUND, SESSION_ID, 2, SessionClose(), contextSessionProps = sessionContext.avro),
            ),
            0,
            mutableListOf(),
            sessionId = SESSION_ID,
            counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            requireClose = false
        )

        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(closingSessionState)

        val closingList = flowSessionManager.getSessionsWithNextMessageClose(checkpoint, listOf(SESSION_ID))
        assertThat(closingList).isEmpty()
    }


    @Test
    fun `send error messages - exception message set`() {
        val instant = Instant.now()
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))

        whenever(sessionManager.processMessageToSend(any(), eq(sessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }
        whenever(sessionManager.processMessageToSend(any(), eq(anotherSessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    anotherSessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }


        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionError(ExceptionEnvelope(IllegalArgumentException::class.qualifiedName, "errorMessage")),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val anotherExpectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            ANOTHER_SESSION_ID,
            sequenceNum = null,
            payload = SessionError(ExceptionEnvelope(IllegalArgumentException::class.qualifiedName, "errorMessage")),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val sessionStates = flowSessionManager.sendErrorMessages(
            checkpoint,
            listOf(SESSION_ID, ANOTHER_SESSION_ID),
            IllegalArgumentException("errorMessage"),
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(sessionState), any(), eq(instant), any())
        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(anotherSessionState), any(), eq(instant), any())
        assertEquals(expectedSessionEvent, sessionStates[0].sendEventsState.undeliveredMessages.single())
        assertEquals(anotherExpectedSessionEvent, sessionStates[1].sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `send error messages - null exception message`() {
        val instant = Instant.now()
        whenever(checkpoint.sessions).thenReturn(listOf(sessionState, anotherSessionState))

        whenever(sessionManager.processMessageToSend(any(), eq(sessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    sessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }
        whenever(sessionManager.processMessageToSend(any(), eq(anotherSessionState), any(), any(), any())).then {
            SessionState().apply {
                sendEventsState = SessionProcessState(
                    1,
                    anotherSessionState.sendEventsState.undeliveredMessages.plus(it.getArgument(2) as SessionEvent)
                )
            }
        }

        val expectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            SESSION_ID,
            sequenceNum = null,
            payload = SessionError(
                ExceptionEnvelope(
                    IllegalArgumentException::class.qualifiedName,
                    "No exception message provided."
                )
            ),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val anotherExpectedSessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            ANOTHER_SESSION_ID,
            sequenceNum = null,
            payload = SessionError(
                ExceptionEnvelope(
                    IllegalArgumentException::class.qualifiedName,
                    "No exception message provided."
                )
            ),
            timestamp = instant,
            initiatingIdentity = HOLDING_IDENTITY,
            initiatedIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            null
        )

        val sessionStates = flowSessionManager.sendErrorMessages(
            checkpoint,
            listOf(SESSION_ID, ANOTHER_SESSION_ID),
            IllegalArgumentException(),
            instant
        )

        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(sessionState), any(), eq(instant), any())
        verify(sessionManager).processMessageToSend(eq(FLOW_ID), eq(anotherSessionState), any(), eq(instant), any())
        assertEquals(expectedSessionEvent, sessionStates[0].sendEventsState.undeliveredMessages.single())
        assertEquals(anotherExpectedSessionEvent, sessionStates[1].sendEventsState.undeliveredMessages.single())
    }

    @Test
    fun `send confirm message`() {
        val confirmedSessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            mutableListOf(
                buildSessionEvent(MessageDirection.INBOUND, SESSION_ID, 2, SessionClose(), contextSessionProps = sessionContext.avro),
            ),
            0,
            mutableListOf(),
            sessionId = SESSION_ID,
            counterpartyIdentity = COUNTERPARTY_HOLDING_IDENTITY,
            requireClose = false
        )

        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(confirmedSessionState)

        flowSessionManager.sendCounterpartyInfoResponse(checkpoint, SESSION_ID, emptyKeyValuePairList(), Instant.now())
        verify(sessionManager, times(1)).processMessageToSend(any(), any(), any(), any(), any())
    }

    @Test
    fun `getRequireCloseTrueAndFalse when states are in CONFIRMED or CREATED or CLOSING and requireClose is true or false`() {
        sessionState.status = SessionStateType.CONFIRMED
        anotherSessionState.status = SessionStateType.CREATED

        sessionState.requireClose = true
        anotherSessionState.requireClose = false

        assertEquals(
            Pair(
                listOf(SESSION_ID), listOf(ANOTHER_SESSION_ID)
            ),
            flowSessionManager.getRequireCloseTrueAndFalse(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID)
            )
        )

    }

    @Test
    fun `getRequireCloseTrueAndFalse when states are in CONFIRMED or CREATED or CLOSING and requireClose is true`() {
        sessionState.status = SessionStateType.CLOSING
        anotherSessionState.status = SessionStateType.CLOSING

        sessionState.requireClose = true
        anotherSessionState.requireClose = true

        assertEquals(
            Pair(
                listOf(ANOTHER_SESSION_ID, SESSION_ID), emptyList<String>()
            ),
            flowSessionManager.getRequireCloseTrueAndFalse(
                checkpoint,
                listOf(ANOTHER_SESSION_ID, SESSION_ID)
            )
        )

    }

    @Test
    fun `getRequireCloseTrueAndFalse when states are in ERROR or CLOSED and requireClose is true or false`() {
        sessionState.status = SessionStateType.ERROR
        anotherSessionState.status = SessionStateType.CLOSED

        sessionState.requireClose = true
        anotherSessionState.requireClose = false

        assertEquals(
            Pair(
                emptyList<String>(), emptyList<String>()
            ),
            flowSessionManager.getRequireCloseTrueAndFalse(
                checkpoint,
                listOf(SESSION_ID, ANOTHER_SESSION_ID)
            )
        )

    }

    @Test
    fun getInitiatingAndInitiatedSessions() {
        sessionState.sessionId = "$SESSION_ID-INITIATED"

        assertEquals(
            Pair(
                listOf(anotherSessionState.sessionId), listOf(sessionState.sessionId)
            ),
            flowSessionManager.getInitiatingAndInitiatedSessions(
                (listOf(sessionState.sessionId, anotherSessionState.sessionId))
            )
        )
    }

    @Test
    fun updateStatus() {
        sessionState.status = SessionStateType.CREATED
        assertEquals(
            listOf(sessionState),
            flowSessionManager.getSessionsWithStatus(
                checkpoint,
                listOf(SESSION_ID),
                SessionStateType.CREATED
            )
        )

        anotherSessionState.status = SessionStateType.CONFIRMED
        assertEquals(
            listOf(anotherSessionState),
            flowSessionManager.getSessionsWithStatus(
                checkpoint,
                listOf(ANOTHER_SESSION_ID),
                SessionStateType.CONFIRMED
            )
        )

        flowSessionManager.updateStatus(checkpoint, listOf(SESSION_ID), SessionStateType.CLOSED)
        assertThat(sessionState.status).isEqualTo(SessionStateType.CLOSED)

        flowSessionManager.updateStatus(checkpoint, listOf(ANOTHER_SESSION_ID), SessionStateType.CLOSING)
        assertThat(anotherSessionState.status).isEqualTo(SessionStateType.CLOSING)
    }
}
