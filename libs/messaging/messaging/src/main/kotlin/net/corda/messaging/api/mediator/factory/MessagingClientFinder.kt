package net.corda.messaging.api.mediator.factory

import net.corda.messaging.api.mediator.MessagingClient

/**
 * Messaging client finder is used to access [MessagingClient] by its ID.
 */
fun interface MessagingClientFinder {

    /**
     * @param id Messaging client's ID.
     * @return Messaging client found by given ID.
     */
    fun find(id: String): MessagingClient
}
