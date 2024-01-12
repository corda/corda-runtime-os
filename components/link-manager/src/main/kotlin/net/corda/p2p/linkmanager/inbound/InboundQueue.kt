package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.p2p.linkmanager.ItemWithSource
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class InboundQueue(
    private val processor: InboundMessageProcessor,
    private val publisher: PublisherWithDominoLogic,
    private val executor: Executor = Executors.newSingleThreadExecutor()
): SyncRPCProcessor<LinkInMessage, LinkManagerResponse>, Runnable {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
    }
    data class InboundRequest(
        override val message: LinkInMessage,
        val future: CompletableFuture<LinkManagerResponse>,
    ): InboundMessage
    private val queue = ConcurrentLinkedDeque<InboundRequest>()
    private val lock = ReentrantLock()
    private val hasItems = lock.newCondition()
    private val running = AtomicBoolean(false)

    override fun process(request: LinkInMessage): LinkManagerResponse {
        val complete = CompletableFuture<LinkManagerResponse>()
        val queueElement = InboundRequest(
            request,
            complete,
        )
        queue.addLast(queueElement)
        lock.withLock {
            hasItems.signal()
        }
        return try {
            complete.get()
        } catch (e: Exception) {
            throw e.cause ?: e
        }
    }

    fun start() {
        running.set(true)
        executor.execute(this)
    }

    fun stop() {
        running.set(false)
    }

    override val requestClass = LinkInMessage::class.java
    override val responseClass = LinkManagerResponse::class.java

    override fun run() {
        while (running.get()) {
            val items = generateSequence {
                queue.pollFirst()
            }.toList()
            if (!running.get()) {
                return
            }
            if (items.isEmpty()) {
                hasItems.await(20, TimeUnit.SECONDS)
            } else {
                val responses = processor.handleRequests(items)
                handeResponses(responses)

                //make sure we completed all the futures
                completeUncompleted(items)
            }
        }
    }

    private fun completeUncompleted(items: Collection<InboundRequest>) {
        items.forEach {
            if (!it.future.isDone) {
                it.future.completeExceptionally(
                    CordaRuntimeException("Could not handle request")
                )
            }
        }
    }

    private fun handeResponses(
        responses: List<ItemWithSource<InboundRequest, InboundResponse>>
    ) {
        val messages = responses.flatMap {
            it.item.records
        }
        if (messages.isNotEmpty()) {
            try {
                publisher.publish(messages).forEach {
                    it.join()
                }
            } catch (e: Exception) {
                logger.warn("Failed to publish messages to the bus.", e)
                responses.forEach {
                    it.source.future.completeExceptionally(e)
                }
                return
            }
        }
        responses.forEach {
            val reply = it.item.httpReply ?: LinkManagerResponse(null)
            it.source.future.complete(reply)
        }
    }
}