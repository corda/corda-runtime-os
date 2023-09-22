package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import java.util.concurrent.Callable

/**
 * [ClientTask] sends a [MediatorMessage] to [MessagingClient] selected by [MessageRouter].
 */
class ClientTask<K: Any, S: Any, E: Any>(
    private val message: MediatorMessage<Any>,
    private val messageRouter: MessageRouter,
    val processorTask: ProcessorTask<K, S, E>,
): Callable<ClientTask.Result<K, S, E>> {

    class Result<K: Any, S: Any, E: Any>(
        val clientTask: ClientTask<K, S, E>,
        val replyMessage: MediatorMessage<E>?,
    ) {
        fun hasReply() = replyMessage != null
    }

    override fun call(): Result<K, S, E> {
        val destination = messageRouter.getDestination(message)
        @Suppress("UNCHECKED_CAST")
        val reply = runBlocking {
            with(destination) { client.send(message, endpoint).await() }
        } as MediatorMessage<E>?
        return Result(this, reply)
    }
}