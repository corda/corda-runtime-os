package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.ItemWithSource
import net.corda.utilities.Either
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class InboundRpcProcessorTest {
    private class Request(
        override val message: LinkInMessage?,
    ) : InboundMessage
    private val publisher = mock<PublisherWithDominoLogic> {
        on { publish(any()) } doReturn listOf(CompletableFuture.completedFuture(Unit))
    }
    private val linkInMessage = mock<LinkInMessage>()
    private val request = Request(
        linkInMessage,
    )
    private val requests = listOf(
        request,
    )
    private val reply = mock<LinkManagerResponse>()
    private val inboundResponse = mock<InboundResponse> {
        on { records } doReturn emptyList()
        on { httpReply } doReturn reply
    }
    private val replyWithSource = ItemWithSource(
        inboundResponse,
        request,
    )
    private val processor = mock<InboundMessageProcessor> {
        on { handleRequests(requests) } doReturn listOf(replyWithSource)
    }
    private val failedRequest = mock<LinkInMessage>()
    private val completableFuture = CompletableFuture.completedFuture(reply)
    private val failedFuture = CompletableFuture.failedFuture<LinkManagerResponse>(CordaRuntimeException("Ooops"))
    private val bufferedQueue = mock<BufferedQueue> {
        on { add(linkInMessage) } doReturn completableFuture
        on { add(failedRequest) } doReturn failedFuture
    }

    private val rpcProcessor = InboundRpcProcessor(
        processor,
        publisher,
        bufferedQueue,
    )

    @Test
    fun `process will add message to the queue`() {
        rpcProcessor.process(linkInMessage)

        verify(bufferedQueue).add(linkInMessage)
    }

    @Test
    fun `process will return the correct reply`() {
        val response = rpcProcessor.process(linkInMessage)

        assertThat(response).isSameAs(reply)
    }

    @Test
    fun `process will fail if request had failed`() {
        assertThrows<CordaRuntimeException> {
            rpcProcessor.process(failedRequest)
        }
    }

    @Test
    fun `start will start the buffer`() {
        rpcProcessor.start()

        verify(bufferedQueue).start(rpcProcessor)
    }

    @Test
    fun `stop will stop the buffer`() {
        rpcProcessor.stop()

        verify(bufferedQueue).stop()
    }

    @Test
    fun `handle will publish the records`() {
        rpcProcessor.stop()

        verify(bufferedQueue).stop()
    }

    @Test
    fun `process will publish the records`() {
        val records = listOf(
            mock<Record<String, String>>(),
            mock<Record<String, String>>(),
            mock<Record<String, String>>(),
        )
        whenever(inboundResponse.records).doReturn(records)

        rpcProcessor.handle(requests)

        verify(publisher).publish(records)
    }

    @Test
    fun `process will return the result if publisher passed`() {
        val records = listOf(
            mock<Record<String, String>>(),
            mock<Record<String, String>>(),
            mock<Record<String, String>>(),
        )
        whenever(inboundResponse.records).doReturn(records)

        val replies = rpcProcessor.handle(requests)

        assertThat(
            replies.mapNotNull {
                it.item as? Either.Right
            },
        ).hasSize(1)
    }

    @Test
    fun `process will return error if publisher failed`() {
        val records = listOf(
            mock<Record<String, String>>(),
            mock<Record<String, String>>(),
            mock<Record<String, String>>(),
        )
        whenever(inboundResponse.records).doReturn(records)
        val errors = CompletableFuture.failedFuture<Unit>(CordaRuntimeException("Error"))
        whenever(publisher.publish(records))
            .doReturn(
                listOf(
                    errors,
                ),
            )

        val replies = rpcProcessor.handle(requests)

        assertThat(
            replies.mapNotNull {
                it.item as? Either.Left
            },
        ).hasSize(1)
    }

    @Test
    fun `process will return the correct data`() {
        val replies = rpcProcessor.handle(requests)

        val httpReply = replies.mapNotNull {
            it.item as? Either.Right
        }.map {
            it.b
        }.firstOrNull()
        assertThat(httpReply).isSameAs(reply)
    }

    @Test
    fun `process will create a reply if reply is null`() {
        whenever(inboundResponse.httpReply).doReturn(null)
        val replies = rpcProcessor.handle(requests)

        val httpReply = replies.mapNotNull {
            it.item as? Either.Right
        }.map {
            it.b
        }.firstOrNull()
        assertThat(httpReply).isNotNull()
    }
}
