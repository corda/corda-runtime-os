package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient

/**
 * Messaging client finder allows [MessageRouter] to access [MessagingClient] by its ID. Multi-Source Event Mediator
 * creates messaging clients and provides implementation of this interface to the [MessageRouterFactory].
 */
fun interface MessagingClientFinder {

    /**
     * @param id Messaging client's ID.
     * @return Messaging client found by given ID.
     */
    fun find(id: String): MessagingClient
}
