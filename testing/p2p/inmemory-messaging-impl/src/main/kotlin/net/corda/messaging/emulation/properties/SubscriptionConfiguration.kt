package net.corda.messaging.emulation.properties

import java.time.Duration

data class SubscriptionConfiguration(
    val maxPollSize: Int,
    val threadStopTimeout: Duration,
)
