package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.api.records.Record
import java.util.concurrent.Callable

/**
 * [SendRecordsTask] sends a [MediatorMessage] to [MessagingClient] selected by [MessageRouter].
 */
data class SendRecordsTask(
    private val outputEvents: List<Record<*, *>>,
    private val messageRouter: MessageRouter,
) : Callable<Unit> {

    override fun call() {
        outputEvents.map { event ->
            val mediatorMessage = event.toMessage()
            val destination = messageRouter.getDestination(mediatorMessage)

            with(destination) {
                mediatorMessage.addProperty(MSG_PROP_ENDPOINT, endpoint)
                client.send(mediatorMessage)
            }
        }
    }
}