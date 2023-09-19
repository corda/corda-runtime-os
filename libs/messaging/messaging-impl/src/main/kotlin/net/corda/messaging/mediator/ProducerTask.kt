package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.MessageRouter
import java.util.concurrent.Callable

/**
 * [ProducerTask] sends a [MediatorMessage] to [MediatorProducer] selected by [MessageRouter].
 */
class ProducerTask<K: Any, S: Any, E: Any>(
    private val message: MediatorMessage<Any>,
    private val messageRouter: MessageRouter,
    val processorTask: ProcessorTask<K, S, E>,
): Callable<ProducerTask.Result<K, S, E>> {

    class Result<K: Any, S: Any, E: Any>(
        val producerTask: ProducerTask<K, S, E>,
        val replyMessage: MediatorMessage<E>?,
    ) {
        fun hasReply() = replyMessage != null
    }

    override fun call(): Result<K, S, E> {
        val destination = messageRouter.getDestination(message)
        @Suppress("UNCHECKED_CAST")
        val reply = runBlocking {
            with(destination) { producer.send(message, endpoint).await() }
        } as MediatorMessage<E>?
        return Result(this, reply)
    }
}