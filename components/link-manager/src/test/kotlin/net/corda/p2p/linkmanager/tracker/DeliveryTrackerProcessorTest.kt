package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture

class DeliveryTrackerProcessorTest {
    private val records = listOf(
        EventLogRecord(
            topic = "topic",
            key = "key",
            value = mock<AppMessage>(),
            partition = 1,
            offset = 101,
        ),
        EventLogRecord(
            topic = "topic",
            key = "key",
            value = mock<AppMessage>(),
            partition = 3,
            offset = 3003,
        ),
    )
    private val recordsToForward = listOf(
        EventLogRecord(
            topic = "topic",
            key = "key-2",
            value = mock<AppMessage>(),
            partition = 4,
            offset = 104,
        ),
        EventLogRecord(
            topic = "topic",
            key = "key-3",
            value = mock<AppMessage>(),
            partition = 5,
            offset = 3002,
        ),
    )
    private val recordsToPublish = listOf(
        Record(
            topic = "topic",
            key = "key-4",
            value = 1000,
        ),
    )
    private val outboundMessageProcessor = mock<OutboundMessageProcessor> {
        on { onNext(any()) } doReturn recordsToPublish
    }
    private val future = mock<CompletableFuture<Unit>> {}
    private val publisher = mock<PublisherWithDominoLogic> {
        on { publish(any()) } doReturn listOf(future)
    }
    private val handler = mock<MessagesHandler> {
        on { handleMessagesAndFilterRecords(any()) } doReturn recordsToForward
    }

    private val processor = DeliveryTrackerProcessor(
        outboundMessageProcessor,
        handler,
        publisher,
    )

    @Test
    fun `onNext will send the messages to the handler`() {
        processor.onNext(records)

        verify(handler).handleMessagesAndFilterRecords(records)
    }

    @Test
    fun `onNext will send the messages to the processor`() {
        processor.onNext(records)

        verify(outboundMessageProcessor).onNext(recordsToForward)
    }

    @Test
    fun `onNext will publish the records`() {
        processor.onNext(records)

        verify(publisher).publish(recordsToPublish)
    }

    @Test
    fun `onNext will wait for published records`() {
        processor.onNext(records)

        verify(future).join()
    }

    @Test
    fun `onNext will notify that it handled the messages`() {
        processor.onNext(records)

        verify(handler).handled(records)
    }
}
