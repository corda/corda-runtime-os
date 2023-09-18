package net.corda.messaging.api.mediator

/**
 * Routing destination encapsulate [MediatorProducer] and related data needed to send a [MediatorMessage].
 */
data class RoutingDestination(
    val producer: MediatorProducer,
    val endpoint: String,
) {
    companion object {
        fun routeTo(producer: MediatorProducer, endpoint: String) =
            RoutingDestination(producer, endpoint)
    }
}
