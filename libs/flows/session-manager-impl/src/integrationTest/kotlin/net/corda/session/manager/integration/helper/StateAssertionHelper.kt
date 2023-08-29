package net.corda.session.manager.integration.helper

import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.integration.SessionParty
import org.assertj.core.api.Assertions.assertThat

fun SessionParty.assertStatus(expectedStatus: SessionStateType?) {
    assertThat(sessionState?.status).isEqualTo(expectedStatus)
}

fun SessionParty.assertAllMessagesDelivered() {
    this.assertSentMessagesDelivered()
    this.assertReceivedMessagesDelivered()
}

fun SessionParty.assertLastSentSeqNum( expectedSeqNum: Int) {
    assertThat(sessionState?.sendEventsState?.lastProcessedSequenceNum).isEqualTo(expectedSeqNum)
}

fun SessionParty.assertLastReceivedSeqNum( expectedSeqNum: Int) {
    assertThat(sessionState?.receivedEventsState?.lastProcessedSequenceNum).isEqualTo(expectedSeqNum)

}

fun SessionParty.assertSentMessagesDelivered() {
    assertThat(sessionState?.sendEventsState?.undeliveredMessages).isEmpty()
}

fun SessionParty.assertReceivedMessagesDelivered() {
    assertThat(sessionState?.receivedEventsState?.undeliveredMessages).isEmpty()
}