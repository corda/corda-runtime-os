package net.corda.messaging.api.mediator

/**
 * Routing destination encapsulate [MediatorProducer] and related data needed to send a [MediatorMessage].
 */
data class RoutingDestination(
    val producer: MediatorProducer,
    val address: String,
) {
    companion object {
        fun routeTo(producer: MediatorProducer, address: String) =
            RoutingDestination(producer, address)
    }
}
