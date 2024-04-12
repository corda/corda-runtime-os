package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.app.AppMessage
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AckMessageProcessorTest {
    private val partitionState = mock<PartitionState>()
    private val partitionsStates = mock<PartitionsStates> {
        on { get(any()) } doReturn partitionState
    }

    private val processor = AckMessageProcessor(partitionsStates)

    @Test
    fun `ackReceived stops tracking message on receiving ack`() {
        val messageId = "test-id"
        val partition = 10
        val ack = MessageAck(AuthenticatedMessageAck(messageId))

        processor.ackReceived(ack, partition)

        verify(partitionsStates).get(partition)
        verify(partitionState).untrackMessage(messageId)
    }

    @Test
    fun `ackReceived ignores ack with unknown type`() {
        val ack = MessageAck("unknown")

        processor.ackReceived(ack, 10)

        verify(partitionsStates, never()).get(any())
        verify(partitionState, never()).untrackMessage(any())
    }

    @Test
    fun `forwardAckMessageToP2POut returns correct record`() {
        val messageId = "test-id"
        val ack = MessageAck(AuthenticatedMessageAck(messageId))

        val record = AckMessageProcessor.forwardAckMessageToP2POut(messageId, ack)

        assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
        assertThat(record.key).isEqualTo(messageId)
        assertThat(record.value).isEqualTo(AppMessage(ack))

    }
}
