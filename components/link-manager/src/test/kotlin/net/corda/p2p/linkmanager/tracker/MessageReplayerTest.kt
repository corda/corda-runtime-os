package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.concurrent.CompletableFuture

class MessageReplayerTest {
    private val key = "key"
    private val message = mock<AuthenticatedMessage>()

    private val cache = mock<DataMessageCache> {
        on { get(key) } doReturn message
    }
    private val records = listOf(
        Record(
            "topic",
            "key1",
            "value1",
        ),
        Record(
            "topic",
            "key2",
            "value2",
        ),
    )
    private val outboundMessageProcessor = mock<OutboundMessageProcessor> {
        on {
            processReplayedAuthenticatedMessage(
                AuthenticatedMessageAndKey(
                    message,
                    key,
                ),
            )
        } doReturn records
    }
    private val future = mock<CompletableFuture<Unit>> { }
    private val publisher = mock<PublisherWithDominoLogic> {
        on { publish(records) } doReturn listOf(future)
    }

    private val replayer = MessageReplayer(
        publisher,
        outboundMessageProcessor,
        cache,
    )

    @Test
    fun `invoke will wait for the publisher to publish the records`() {
        replayer.invoke(key, key)

        verify(future).join()
    }

    @Test
    fun `invoke will do nothing if a message can not be found`() {
        replayer.invoke("id", "id")

        verifyNoInteractions(outboundMessageProcessor)
    }
}
