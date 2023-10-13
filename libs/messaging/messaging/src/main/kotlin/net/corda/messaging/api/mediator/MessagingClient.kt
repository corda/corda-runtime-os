package net.corda.messaging.api.mediator

/**
 * Multi-source event mediator messaging client.
 */
interface MessagingClient : AutoCloseable {
    companion object {
        /** Name of the property for specifying the endpoint string */
        const val MSG_PROP_ENDPOINT = "clientEndpoint"

        /** Name of the property for specifying the message key */
        const val MSG_PROP_KEY = "key"
    }

    /**
     * Messaging client's unique ID.
     */
    val id: String

    /**
     * Sends a generic [MediatorMessage] and returns any result/error through a response.
     *
     * @param message The [MediatorMessage] to send.
     * @return Computation result, or null if the destination doesn't provide a response.
     * */
    fun send(message: MediatorMessage<*>): MediatorMessage<*>?
}
