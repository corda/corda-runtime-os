package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

/**
 * [ClientTask] sends a [MediatorMessage] to [MessagingClient] selected by [MessageRouter].
 */
data class ClientTask<K : Any, S : Any, E : Any>(
    private val message: MediatorMessage<Any>,
    private val messageRouter: MessageRouter,
    val processorTaskResult: ProcessorTask.Result<K, S, E>,
) : Callable<ClientTask.Result<K, S, E>> {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    class Result<K : Any, S : Any, E : Any>(
        val clientTask: ClientTask<K, S, E>,
        val replyMessage: MediatorMessage<E>?,
    ) {
        val hasReply get() = replyMessage != null
        val key get() = clientTask.processorTaskResult.key
        val processorTask get() = clientTask.processorTaskResult.processorTask
        val processorTaskResult get() = clientTask.processorTaskResult
    }

    override fun call(): Result<K, S, E> {
        log.info("Processing message [$message]")
        val destination = messageRouter.getDestination(message)
        log.info("Destination [$destination]")

        @Suppress("UNCHECKED_CAST")
        val reply = runBlocking {
            with(destination) {
                message.addProperty(MSG_PROP_ENDPOINT, endpoint)
                log.info("Sending message [$message]")
                client.send(message).await() as MediatorMessage<E>?
            }
        }
        log.info("Reply [$reply]")
        return Result(this, reply)
    }
}