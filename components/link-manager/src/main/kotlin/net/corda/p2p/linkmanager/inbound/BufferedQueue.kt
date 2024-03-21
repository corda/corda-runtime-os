package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.p2p.linkmanager.ItemWithSource
import net.corda.utilities.Either
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class BufferedQueue(
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) : Runnable {
    interface Handler {
        fun <T : InboundMessage> handle(
            items: Collection<T>,
        ): Collection<ItemWithSource<T, Either<Throwable, LinkManagerResponse>>>
    }
    private data class Request(
        override val message: LinkInMessage,
        val future: CompletableFuture<LinkManagerResponse>,
    ) : InboundMessage
    private val queue = ConcurrentLinkedQueue<Request>()
    private val lock = ReentrantLock()
    private val hasItems = lock.newCondition()
    private val running = AtomicReference<Handler>()

    fun start(handler: Handler) {
        running.set(handler)
        executor.execute(this)
    }

    fun stop() {
        running.set(null)
        lock.withLock {
            hasItems.signalAll()
        }
    }

    fun add(request: LinkInMessage): CompletableFuture<LinkManagerResponse> {
        val complete = CompletableFuture<LinkManagerResponse>()
        val queueElement = Request(
            request,
            complete,
        )
        queue.offer(queueElement)
        lock.withLock {
            hasItems.signalAll()
        }
        return complete
    }

    override fun run() {
        while (running.get() != null) {
            val items = generateSequence {
                queue.poll()
            }.toList()
            if (running.get() == null) {
                return
            }
            if (items.isEmpty()) {
                lock.withLock {
                    hasItems.awaitUninterruptibly()
                }
            } else {
                handleItems(items)
                // make sure we completed all the futures
                completeUncompleted(items)
            }
        }

        // Empty the queue with exceptions
        emptyQueue()
    }
    private fun completeUncompleted(items: Collection<Request>) {
        items.forEach {
            if (!it.future.isDone) {
                it.future.completeExceptionally(
                    CordaRuntimeException("Could not handle request"),
                )
            }
        }
    }

    private fun emptyQueue() {
        generateSequence {
            queue.poll()
        }.forEach {
            it.future.completeExceptionally(
                CordaRuntimeException("Queue stopped processing events"),
            )
        }
    }

    private fun handleItems(items: Collection<Request>) {
        val handler = running.get()
        handler?.handle(items)?.forEach {
            when (val item = it.item) {
                is Either.Left -> {
                    it.source.future.completeExceptionally(item.a)
                }
                is Either.Right -> {
                    it.source.future.complete(item.b)
                }
            }
        }
    }
}
