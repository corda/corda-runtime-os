package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.app.AppMessage
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class AckMessageProcessor(
    private val partitionsStates: PartitionsStates,
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun forwardAckMessageToP2pOut(messageId: String, messageAck: MessageAck): Record<String, AppMessage> {
            logger.trace("Forwarding ack for message '{}' to '{}'.", messageId, P2P_OUT_TOPIC)
            return Record(P2P_OUT_TOPIC, messageId, AppMessage(messageAck))
        }
    }

    fun ackReceived(messageAck: MessageAck, partition: Int) {
        when (val ack = messageAck.ack) {
            is AuthenticatedMessageAck -> processAck(ack, partition)
            else -> logger.warn("Failed to process message ack. Cause: Unexpected ack type.")
        }
    }

    private fun processAck(ack: AuthenticatedMessageAck, partition: Int) {
        val messageId = ack.messageId
        logger.trace("Processing ack for message '{}' received on partition '{}'.", messageId, partition)
        partitionsStates.get(partition)?.untrackMessage(messageId) ?: logger.warn(
            "Failed to process message ack for '{}'. Cause: Could not find partition state for partition '{}'. ",
            messageId,
            partition
        )
    }
}
