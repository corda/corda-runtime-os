package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.linkmanager.LinkManagerResponse
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.p2p.linkmanager.ItemWithSource
import net.corda.utilities.Either
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

internal class InboundRpcProcessor(
    private val processor: InboundMessageProcessor,
    private val publisher: PublisherWithDominoLogic,
    private val queue: BufferedQueue = BufferedQueue(),
) : SyncRPCProcessor<LinkInMessage, LinkManagerResponse>, BufferedQueue.Handler {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
    }

    override fun process(request: LinkInMessage): LinkManagerResponse {
        val complete = queue.add(request)

        return try {
            complete.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    fun start() {
        queue.start(this)
    }

    fun stop() {
        queue.stop()
    }

    override val requestClass = LinkInMessage::class.java
    override val responseClass = LinkManagerResponse::class.java

    override fun <T : InboundMessage> handle(
        items: Collection<T>,
    ): Collection<ItemWithSource<T, Either<Throwable, LinkManagerResponse>>> {
        val responses = processor.handleRequests(items)
        return handeResponses(responses)
    }

    private fun <T : InboundMessage> handeResponses(
        responses: List<ItemWithSource<T, InboundResponse>>,
    ): Collection<ItemWithSource<T, Either<Throwable, LinkManagerResponse>>> {
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
                return responses.map {
                    ItemWithSource(
                        Either.Left(e.cause ?: e),
                        it.source,
                    )
                }
            }
        }
        return responses.map {
            ItemWithSource(
                Either.Right(it.item.ack?.asLeft() ?: LinkManagerResponse(null)),
                it.source,
            )
        }
    }
}
