package net.corda.session.manager.impl

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionStateType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionManagerImplTest {

    private val sessionManager = SessionManagerImpl()

    @Test
    fun testGetNextReceivedEvent() {
        val sessionState = buildSessionState(SessionStateType.CONFIRMED,
            1,
            listOf(
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId",1, null),
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId",3, null),
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId",4, null),
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
        val sessionState = buildSessionState(SessionStateType.CONFIRMED,
            1,
            listOf(
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId",3, null),
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId",4, null),
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
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 1, null),
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 3, null),
                SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 4, null),
            ),
            0,
            listOf()
        )
        val outputState = sessionManager.acknowledgeReceivedEvent(sessionState, 1)
        assertThat(outputState.receivedEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(outputState.receivedEventsState.undeliveredMessages.find{ it.sequenceNum == 1}).isNull()
    }


    @Test
    fun testGetMessagesToSend() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(
                SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", 2, SessionData()),
                SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", 3, SessionData()),
                SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", 4, SessionAck()),
            ),

        )
        val (outputState, messagesToSend) = sessionManager.getMessagesToSend(sessionState)
        assertThat(messagesToSend.size).isEqualTo(3)
        assertThat(outputState.sendEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(outputState.sendEventsState.undeliveredMessages.find{ it.payload::class.java == SessionAck::class.java}).isNull()
    }
}