package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.p2p.linkmanager.ItemWithSource
import net.corda.utilities.Either
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.Executor

class BufferedQueueTest {
    private val runnable = argumentCaptor<Runnable>()
    private val items = argumentCaptor<Collection<InboundMessage>>()
    private val handler = mock<BufferedQueue.Handler> {
        on { handle(items.capture()) } doAnswer {
            queue.stop()
            emptyList()
        }
    }
    private val executor = mock<Executor> {
        on { execute(runnable.capture()) } doAnswer {}
    }

    private val queue = BufferedQueue(
        executor,
    )

    @Test
    fun `start will start the runnable`() {
        queue.start(handler)

        assertThat(runnable.firstValue).isSameAs(queue)
    }

    @Test
    fun `add will add items to the list`() {
        queue.start(handler)
        val messages = (1..4).map {
            mock<LinkInMessage>()
        }

        messages.forEach {
            queue.add(it)
        }

        queue.run()

        assertThat(
            items.firstValue.map {
                it.message
            },
        ).containsExactlyInAnyOrderElementsOf(messages)
    }

    @Test
    fun `run will not do anything if it has not started`() {
        assertDoesNotThrow {
            queue.run()
        }
    }

    @Test
    fun `future will be completed successfully if handle was successful`() {
        val message = mock<LinkInMessage>()
        val response = mock<LinkManagerResponse>()
        whenever(handler.handle(any<Collection<InboundMessage>>())).doAnswer {
            queue.stop()
            val source = it.getArgument<Collection<InboundMessage>>(0).first()
            listOf(
                ItemWithSource(
                    Either.Right(response),
                    source,
                ),
            )
        }
        queue.start(handler)
        val future = queue.add(message)

        queue.run()

        assertThat(future).isCompletedWithValue(response)
    }

    @Test
    fun `future will be completed exceptionally if handle was not successful`() {
        val message = mock<LinkInMessage>()
        whenever(handler.handle(any<Collection<InboundMessage>>())).doAnswer {
            queue.stop()
            val source = it.getArgument<Collection<InboundMessage>>(0).first()
            listOf(
                ItemWithSource(
                    Either.Left(IOException("Oops")),
                    source,
                ),
            )
        }
        queue.start(handler)
        val future = queue.add(message)

        queue.run()

        assertThat(future).isCompletedExceptionally()
    }

    @Test
    fun `future will be completed exceptionally if handler did not reply`() {
        val message = mock<LinkInMessage>()
        whenever(handler.handle(any<Collection<InboundMessage>>())).doAnswer {
            queue.stop()
            emptyList()
        }
        queue.start(handler)
        val future = queue.add(message)

        queue.run()

        assertThat(future).isCompletedExceptionally()
    }
}
