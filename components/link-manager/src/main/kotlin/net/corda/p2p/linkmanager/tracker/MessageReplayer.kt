package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.markers.AppMessageMarker
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
        logger.info("TTT replaying: $messageId")
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
        records.forEach {
            when (val value = it.value) {
                is AppMessageMarker ->
                    logger.info("TTT \t sending replaying marker: ${it.key} - ${value.marker?.javaClass}")
                is LinkOutMessage ->
                    logger.info("TTT \t sending replaying: link out ${it.key} - ${value.payload?.javaClass}")
                is AppMessage ->
                    logger.info("TTT \t sending replaying: app message ${it.key} - ${value.message?.javaClass}")
                else ->
                    logger.info("TTT \t sending replaying: ${it.key} to ${it.topic}, ${value?.javaClass}")
            }
        }
        publisher.publish(
            records,
        ).forEach {
            it.join()
        }
    }
}
