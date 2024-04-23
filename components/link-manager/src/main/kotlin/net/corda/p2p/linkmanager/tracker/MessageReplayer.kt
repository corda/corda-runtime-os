package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import org.slf4j.LoggerFactory

internal class MessageReplayer(
    private val publisher: PublisherWithDominoLogic,
    private val outboundMessageProcessor: OutboundMessageProcessor,
    private val cache: DataMessageCache,
) : (String, String) -> Unit {
    private companion object {
        val logger = LoggerFactory.getLogger(MessageReplayer::class.java)
    }
    override fun invoke(
        messageId: String,
        key: String,
    ) {
        logger.debug("Replaying message '$messageId' with key: '$key'")
        val authenticatedMessage = cache.get(key)
        if (authenticatedMessage == null) {
            logger.warn("Cannot replay message '$messageId' with key: '$key', no such message")
            return
        }

        val messageAndKey = AuthenticatedMessageAndKey(
            authenticatedMessage,
            key,
        )
        val records = outboundMessageProcessor.processReplayedAuthenticatedMessage(messageAndKey)
        publisher.publish(
            records,
        ).forEach {
            it.join()
        }
    }
}
