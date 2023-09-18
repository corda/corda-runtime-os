package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.MessageRouter

/**
 * Factory for creating [MessageRouter]s.
 */
fun interface MessageRouterFactory {

    /**
     * Creates a new instance of [MessageRouter]. Provided [MediatorProducerFinder] is used to find [MediatorProducer]s
     * by their names. Example:
     *
     * ```
     * MessageRouterFactory { producerFinder ->
     *     val messageBusProducer = producerFinder.find("MessageBusProducer")
     *
     *     MessageRouter { message ->
     *         when (message.body) {
     *             is FlowEvent -> RoutingDestination(messageBusProducer, "flow.event.topic")
     *             else -> throw IllegalStateException("No route defined for message $message")
     *         }
     *     }
     * }
     * ```
     *
     * @param producerFinder Producer finder
     * @return created message router.
     */
    fun create(producerFinder: MediatorProducerFinder): MessageRouter
}
