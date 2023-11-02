package net.corda.messaging.api.mediator

/**
 * Routing destination encapsulate [MessagingClient] and related data needed to send a [MediatorMessage].
 */
data class RoutingDestination(
    val client: MessagingClient,
    val endpoint: String,
    val type: Type
) {
    enum class Type {
        SYNCHRONOUS, ASYNCHRONOUS
    }

    companion object {
        fun routeTo(client: MessagingClient, endpoint: String, type: Type) =
            RoutingDestination(client, endpoint, type)
    }
}
