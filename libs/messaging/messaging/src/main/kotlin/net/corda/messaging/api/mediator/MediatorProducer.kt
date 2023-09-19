package net.corda.messaging.api.mediator

import kotlinx.coroutines.Deferred

/**
 * Multi-source event mediator message producer.
 */
interface MediatorProducer : AutoCloseable {
    /**
     * Producer's unique ID.
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
