package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient

/**
 * Factory for creating [MessageRouter]s.
 */
fun interface MessageRouterFactory {

    /**
     * Creates a new instance of [MessageRouter]. Provided [MessagingClientFinder] is used to find [MessagingClient]s
     * by their IDs. Example:
     *
     * ```
     * MessageRouterFactory { clientFinder ->
     *     val messageBusClient = clientFinder.find("MessageBusClient")
     *
     *     MessageRouter { message ->
     *         when (message.payload) {
     *             is FlowMapperEvent -> routeTo(messageBusClient, FLOW_MAPPER_EVENT_TOPIC)
     *             is FlowStatus -> routeTo(messageBusClient, FLOW_STATUS_TOPIC)
     *             else -> throw IllegalStateException("No route defined for message $message")
     *         }
     *     }
     * }
     * ```
     *
     * @param clientFinder Messaging client finder.
     * @return created message router.
     */
    fun create(clientFinder: MessagingClientFinder): MessageRouter
}
