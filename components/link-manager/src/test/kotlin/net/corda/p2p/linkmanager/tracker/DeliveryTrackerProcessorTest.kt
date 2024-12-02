package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class DeliveryTrackerProcessorTest {
    private val outboundMessageProcessor = mock<OutboundMessageProcessor> {}
    private val partitionsStates = mock<PartitionsStates> {}
    private val publisher = mock<PublisherWithDominoLogic> {}
    private val processor = DeliveryTrackerProcessor(
        outboundMessageProcessor,
        partitionsStates,
        publisher,
    )
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

    @Test
    fun `onNext will send the messages to the outbound processor`() {
        processor.onNext(records)

        verify(outboundMessageProcessor).onNext(records)
    }

    @Test
    fun `onNext will update the states before sending`() {
        processor.onNext(records)

        verify(partitionsStates).read(records)
    }

    @Test
    fun `onNext will update the states after sending`() {
        processor.onNext(records)

        verify(partitionsStates).sent(records)
    }

    @Test
    fun `onNext will publish the records and wait publication`() {
        val replies = listOf(
            Record(
                topic = "topic",
                key = "key",
                value = "",
            ),
            Record(
                topic = "topic",
                key = "key",
                value = "another",
            ),
        )
        val future = mock<CompletableFuture<Unit>>()
        whenever(outboundMessageProcessor.onNext(records)).doReturn(replies)
        whenever(publisher.publish(replies)).doReturn(listOf(future))
        processor.onNext(records)

        verify(future).join()
    }
}
