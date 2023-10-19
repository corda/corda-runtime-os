package net.corda.messaging.mediator

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import org.slf4j.Logger
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
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        val destination = messageRouter.getDestination(message)

        // TODO remove logging that was added for debug purposes
        val key = processorTaskResult.key
        when (val eventValue = message.payload) {
            is FlowEvent -> {
                val eventType = eventValue.payload::class.java.simpleName
                log.info("Sending event: FlowEvent:$eventType [${key}] to [${destination.endpoint}]")
            }

            is FlowMapperEvent -> {
                val eventType = eventValue.payload
                val eventTypeName = eventType::class.java.simpleName
                val eventSubtypeName = if (eventType is FlowEvent) ":${eventType::class.java.simpleName}" else ""
                log.info("Sending event: FlowMapperEvent:$eventTypeName$eventSubtypeName [${key}] " +
                        "to [${destination.endpoint}]")
            }

            else -> {
                val eventType = eventValue?.let { it::class.java.simpleName }
                log.info("Sending event: $eventType [${key}] to [${destination.endpoint}]")
            }
        }

        @Suppress("UNCHECKED_CAST")
        val reply = with(destination) {
            message.addProperty(MSG_PROP_ENDPOINT, endpoint)
            client.send(message) as MediatorMessage<E>?
        }
        return Result(this, reply)
    }
}