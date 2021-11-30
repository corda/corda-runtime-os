package net.corda.session.reorder

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReorderHandlerTest {
    
    @Test
    fun `test null state and sequence != 1`() {
        val reorderHandler = ReorderHandler()
        val sessionEvent = getSessionEvent(2)

        val (state, nextEvents) = reorderHandler.processEvent(null, sessionEvent)

        assertThat(state).isNull()
        assertThat(nextEvents).isEqualTo(emptyList<SessionEvent>())
    }

    @Test
    fun `test null state and valid event`() {
        val reorderHandler = ReorderHandler()
        val sessionEvent = getSessionEvent(1)

        val (state, nextEvents) = reorderHandler.processEvent(null, sessionEvent)

        assertThat(state?.lastProcessedSequenceNum).isEqualTo(1)
        assertThat(state?.undeliveredMessages).isEqualTo(listOf(sessionEvent))
        assertThat(nextEvents).isEqualTo(listOf(sessionEvent))
    }

    @Test
    fun `test sequence less than expected`() {
        val reorderHandler = ReorderHandler()
        val sessionEvent = getSessionEvent(2)

        val receivedEventsState = SessionProcessState()
        receivedEventsState.lastProcessedSequenceNum = 3
        receivedEventsState.undeliveredMessages = emptyList()

        val (state, nextEvents) = reorderHandler.processEvent(receivedEventsState, sessionEvent)

        assertThat(state?.lastProcessedSequenceNum).isEqualTo(3)
        assertThat(state?.undeliveredMessages).isEqualTo(emptyList<SessionEvent>())
        assertThat(nextEvents).isEqualTo(emptyList<SessionEvent>())
    }

    @Test
    fun `test event sequence is expected next with additional expected buffered, signal completion`() {
        val reorderHandler = ReorderHandler()
        val sessionEvent = getSessionEvent(3)
        val expectedEvents = listOf(getSessionEvent(3), getSessionEvent(4), getSessionEvent(5))

        val receivedEventsState = SessionProcessState()
        receivedEventsState.lastProcessedSequenceNum = 2
        receivedEventsState.undeliveredMessages = listOf(getSessionEvent(4), getSessionEvent(5))

        val (state, nextEvents) = reorderHandler.processEvent(receivedEventsState, sessionEvent)
        assertThat(state?.lastProcessedSequenceNum).isEqualTo(5)
        assertThat(state?.undeliveredMessages).isEqualTo(expectedEvents)
        assertThat(nextEvents).isEqualTo(expectedEvents)

        val nextEventsInSequence = reorderHandler.getNextEventsInSequence(state!!)
        assertThat(nextEventsInSequence).isEqualTo(expectedEvents)

        val updatedState = reorderHandler.signalDeliveredEvents(state, 5)
        assertThat(updatedState.lastProcessedSequenceNum).isEqualTo(5)
        assertThat(updatedState.undeliveredMessages).isEqualTo(emptyList<SessionEvent>())

    }

    @Test
    fun `test event sequence is higher than expected with additional buffered, try invalid signal of completion`() {
        val expectedUndeliveredMessages = listOf(getSessionEvent(4), getSessionEvent(5), getSessionEvent(6))
        val reorderHandler = ReorderHandler()
        val sessionEvent = getSessionEvent(6)

        val receivedEventsState = SessionProcessState()
        receivedEventsState.lastProcessedSequenceNum = 2
        receivedEventsState.undeliveredMessages = listOf(getSessionEvent(4), getSessionEvent(5))

        val (state, nextEvents) = reorderHandler.processEvent(receivedEventsState, sessionEvent)

        assertThat(state?.lastProcessedSequenceNum).isEqualTo(2)
        assertThat(state?.undeliveredMessages).isEqualTo(expectedUndeliveredMessages)
        assertThat(nextEvents).isEqualTo(emptyList<SessionEvent>())

        val nextEventsInSequence = reorderHandler.getNextEventsInSequence(state!!)
        assertThat(nextEventsInSequence).isEqualTo(emptyList<SessionEvent>())


        assertThrows<CordaRuntimeException> {
            reorderHandler.signalDeliveredEvents(state, 6)
        }
    }

    private fun getSessionEvent(sequenceNum: Int): SessionEvent {
        val sessionEvent = SessionEvent()
        sessionEvent.sequenceNum = sequenceNum
        return sessionEvent
    }
}
