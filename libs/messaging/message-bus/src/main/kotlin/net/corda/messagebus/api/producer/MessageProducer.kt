package net.corda.messagebus.api.producer

interface MessageProducer : AutoCloseable {
    /**
     * Defines the callback for post-send events. If there was an exception it will be provided on this callback.
     */
    fun interface Callback {
        fun onCompletion(response: CordaMessage<*>?, exception: Exception?)
    }

    /**
     * Asynchronously sends a generic [CordaMessage], and invoke the provided callback when the message has been acknowledged.
     * If the developer would like this to be a blocking call, this can be achieved through the callback.
     *
     * @param message The message to send.
     * @param callback A user-supplied callback to execute when teh record has been successfully sent or an error has occurred.
     */
    fun send(message: CordaMessage<*>, callback: Callback?)

    /**
     * Send a batch of [CordaMessage] instances to their respective destinations. These should all be of the same type
     * (E.g. Kafka, DB, RPC).
     *
     * @param messages the list of [CordaMessage] to be sent.
     */
    fun sendMessages(messages: List<CordaMessage<*>>)
}