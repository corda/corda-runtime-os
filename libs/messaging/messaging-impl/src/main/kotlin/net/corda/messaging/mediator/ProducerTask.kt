package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.Message
import net.corda.messaging.api.mediator.MessageRouter
import java.util.concurrent.Callable

/**
 * [ProducerTask] sends a [Message] to [MediatorProducer] selected by [MessageRouter].
 */
class ProducerTask<K: Any, S: Any, E: Any>(
    private val message: Message,
    private val messageRouter: MessageRouter,
    val processorTask: ProcessorTask<K, S, E>,
): Callable<ProducerTask.Result<K, S, E>> {

    class Result<K: Any, S: Any, E: Any>(
        val producerTask: ProducerTask<K, S, E>,
        val replyMessage: Message?,
    ) {
        fun hasReply() = replyMessage != null
    }

    override fun call(): Result<K, S, E> {
        val destination = messageRouter.getDestination(message)
        val reply = with(destination) { producer.send(message, address) }
        return Result(this, reply.reply)
    }
}