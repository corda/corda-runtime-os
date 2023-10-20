package net.corda.messaging.api.mediator

/**
 * Routing destination encapsulate [MessagingClient] and related data needed to send a [MediatorMessage].
 */
data class RoutingDestination(
    val client: MessagingClient,
    val endpoint: String,
) {
    companion object {
        fun routeTo(client: MessagingClient, endpoint: String) =
            RoutingDestination(client, endpoint)
    }
}
