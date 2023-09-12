package net.corda.messagebus.api.producer

import kotlinx.coroutines.Deferred

interface MessageProducer : AutoCloseable {
    /**
     * Asynchronously sends a generic [CordaMessage], and returns any result/error through a [Deferred] response.
     *
     * @param message The [CordaMessage] to send.
     * @return [Deferred] instance representing the asynchronous computation result, or null if the destination doesn't
     * provide a response.
     * */
    fun send(message: CordaMessage<*>) : Deferred<CordaMessage<*>?>
}
