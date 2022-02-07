package net.corda.session.manager.integration.helper

import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.integration.SessionParty
import org.assertj.core.api.Assertions.assertThat

fun assertStatus(sessionParty: SessionParty, expectedStatus: SessionStateType) {
    assertThat(sessionParty.sessionState?.status).isEqualTo(expectedStatus)
}

fun assertAllMessagesDelivered(sessionParty: SessionParty) {
    assertSentMessagesDelivered(sessionParty)
    assertReceivedMessagesDelivered(sessionParty)
}

fun assertLastSentSeqNum(sessionParty: SessionParty, expectedSeqNum: Int) {
    assertThat(sessionParty.sessionState?.sendEventsState?.lastProcessedSequenceNum).isEqualTo(expectedSeqNum)
}

fun assertLastReceivedSeqNum(sessionParty: SessionParty, expectedSeqNum: Int) {
    assertThat(sessionParty.sessionState?.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(expectedSeqNum)

}

fun assertSentMessagesDelivered(sessionParty: SessionParty) {
    assertThat(sessionParty.sessionState?.sendEventsState?.undeliveredMessages).isEmpty()
}

fun assertReceivedMessagesDelivered(sessionParty: SessionParty) {
    assertThat(sessionParty.sessionState?.receivedEventsState?.undeliveredMessages).isEmpty()
}