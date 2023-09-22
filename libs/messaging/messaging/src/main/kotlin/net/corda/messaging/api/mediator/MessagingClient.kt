package net.corda.messaging.api.mediator

import kotlinx.coroutines.Deferred

/**
 * Multi-source event mediator messaging client.
 */
interface MessagingClient : AutoCloseable {
    /**
     * Messaging client's unique ID.
     */
    val id: String

    /**
     * Asynchronously sends a generic [MediatorMessage], and returns any result/error through a [Deferred] response.
     *
     * @param message The [MediatorMessage] to send.
     * @return [Deferred] instance representing the asynchronous computation result, or null if the destination doesn't
     * provide a response.
     * */
    fun send(message: MediatorMessage<*>) : Deferred<MediatorMessage<*>?>
}
