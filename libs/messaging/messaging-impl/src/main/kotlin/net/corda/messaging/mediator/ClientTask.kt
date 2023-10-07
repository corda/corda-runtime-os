package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT

/**
 * [ClientTask] sends a [MediatorMessage] to [MessagingClient] selected by [MessageRouter].
 */
data class ClientTask<K : Any, S : Any, E : Any>(
    private val message: MediatorMessage<Any>,
    private val messageRouter: MessageRouter,
    val processorTaskResult: ProcessorTask.Result<K, S, E>,
) : TaskManagerHelper.SuspendCallable<ClientTask.Result<K, S, E>> {

    class Result<K : Any, S : Any, E : Any>(
        val clientTask: ClientTask<K, S, E>,
        val replyMessage: MediatorMessage<E>?,
    ) {
        val hasReply get() = replyMessage != null
        val key get() = clientTask.processorTaskResult.key
        val processorTask get() = clientTask.processorTaskResult.processorTask
        val processorTaskResult get() = clientTask.processorTaskResult
    }

    override suspend fun call(): Result<K, S, E> {
        val destination = messageRouter.getDestination(message)

        @Suppress("UNCHECKED_CAST")
        val reply =
            with(destination) {
                message.addProperty(MSG_PROP_ENDPOINT, endpoint)
                client.send(message).await() as MediatorMessage<E>?
            }
        return Result(this, reply)
    }
}