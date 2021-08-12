package net.corda.messaging.emulation.properties

data class SubscriptionConfiguration(
    val pollSize: Int,
    val threadStopTimeout: Long,
)
