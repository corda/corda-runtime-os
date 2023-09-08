package net.corda.messagebus.api.producer

import kotlinx.coroutines.Deferred

interface MessageProducer : AutoCloseable {
    /**
     * Defines the callback for post-send events. If there was an exception it will be provided on this callback.
     */
    fun interface Callback {
        fun onCompletion(exception: Exception?)
    }

    /**
     * Asynchronously sends a generic [CordaMessage], and invoke the provided callback when the message has been acknowledged.
     * If the developer would like this to be a blocking call, this can be achieved through the callback.
     *
     * @param message The [CordaMessage] to send.
     * @return [Deferred] instance representing the asynchronous computation result, or null if the destination doesn't
     * provide a response.
     * */
    fun send(message: CordaMessage<*>, callback: Callback?) : Deferred<*>?

    /**
     * Send a batch of [CordaMessage] instances to their respective destinations. These should all be of the same type
     * (E.g. Kafka, DB, RPC).
     *
     * @param messages the list of [CordaMessage] to be sent.
     * @return List of [Deferred] instances representing the asynchronous computation results, or null if the
     * destination doesn't provide a response.
     */
    fun sendMessages(messages: List<CordaMessage<*>>) : List<Deferred<*>>?
}
