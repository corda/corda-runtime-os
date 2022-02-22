package net.corda.session.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionManagerImplTest {

    private val sessionManager = SessionManagerImpl()
    private val testResendWindow = 5000L
    private val testHeartbeatTimeout = 30000L
    private val testConfig = ConfigFactory.empty()
        .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(testResendWindow))
        .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(testHeartbeatTimeout))
    private val configFactory = SmartConfigFactory.create(testConfig)
    private val testSmartConfig = configFactory.create(testConfig)

    @Test
    fun testGetNextReceivedEvent() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            1,
            listOf(
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 1, null),
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, null),
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 4, null),
            ),
            0,
            listOf()
        )
        val outputEvent = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(outputEvent).isNotNull
        assertThat(outputEvent!!.sequenceNum).isEqualTo(1)
    }

    @Test
    fun testGetNextReceivedEventOutOfOrder() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            1,
            listOf(
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, null),
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 4, null),
            ),
            0,
            listOf()
        )
        val outputEvent = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(outputEvent).isNull()
    }


    @Test
    fun testAcknowledgeReceivedEvent() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            1,
            listOf(
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 1, null),
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, null),
                SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 4, null),
            ),
            0,
            listOf()
        )
        val outputState = sessionManager.acknowledgeReceivedEvent(sessionState, 1)
        assertThat(outputState.receivedEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(outputState.receivedEventsState.undeliveredMessages.find { it.sequenceNum == 1 }).isNull()
    }


    @Test
    fun `Get messages with datas, error and acks with timestamps in the future and past`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(
                SessionEvent(MessageDirection.OUTBOUND, instant.minusMillis(50), "sessionId", 2, SessionData()),
                SessionEvent(MessageDirection.OUTBOUND, instant, "sessionId", 3, SessionData()),
                SessionEvent(MessageDirection.OUTBOUND, instant, "sessionId", null, SessionAck(1)),
                SessionEvent(MessageDirection.OUTBOUND, instant.plusMillis(100), "sessionId", null, SessionAck(2)),
                SessionEvent(MessageDirection.OUTBOUND, instant.plusMillis(100), "sessionId", 4, SessionData()),
            ),
        )
        //validate only messages with a timestamp in the past are returned.
        val (outputState, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig)
        assertThat(messagesToSend.size).isEqualTo(4)
        //validate all acks removed
        assertThat(outputState.sendEventsState.undeliveredMessages.size).isEqualTo(3)
        assertThat(outputState.sendEventsState.undeliveredMessages.filter { it.payload::class.java == SessionAck::class.java }).isEmpty()

        //Validate all acks removed and normal session events are resent
        val (secondOutputState, secondMessagesToSend) = sessionManager.getMessagesToSend(
            sessionState, instant.plusMillis(testResendWindow + 100),
            testSmartConfig
        )
        assertThat(secondMessagesToSend.size).isEqualTo(3)
        assertThat(secondOutputState.sendEventsState.undeliveredMessages.size).isEqualTo(3)
        assertThat(secondOutputState.sendEventsState.undeliveredMessages.filter {
            it.payload::class.java == SessionAck::class.java }
        ).isEmpty()
    }

    @Test
    fun `Send heartbeat`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(),
            instant
        )

        //validate no heartbeat
        val (_, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig)
        assertThat(messagesToSend.size).isEqualTo(0)

        //Validate heartbeat
        val (_, secondMessagesToSend) = sessionManager.getMessagesToSend(
            sessionState, instant.plusMillis(testResendWindow  + 1),
            testSmartConfig
        )

        assertThat(secondMessagesToSend.size).isEqualTo(1)
        val messageToSend = secondMessagesToSend.first()
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionAck::class.java)
        val ack = messageToSend.payload as SessionAck
        assertThat(ack.sequenceNum).isEqualTo(0)
    }

    @Test
    fun `Send error for session timed out`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(),
            instant
        )

        //validate no heartbeat
        val (firstUpdatedState, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig)
        assertThat(messagesToSend.size).isEqualTo(0)
        assertThat(firstUpdatedState.status).isEqualTo(SessionStateType.CONFIRMED)

        //Validate heartbeat
        val (secondUpdatedState, secondMessagesToSend) = sessionManager.getMessagesToSend(
            sessionState, instant.plusMillis(testHeartbeatTimeout  + 1),
            testSmartConfig
        )

        assertThat(secondMessagesToSend.size).isEqualTo(1)
        assertThat(secondUpdatedState.status).isEqualTo(SessionStateType.ERROR)
        val messageToSend = secondMessagesToSend.first()
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionError::class.java)
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
