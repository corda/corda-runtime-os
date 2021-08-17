package net.corda.messaging.emulation.properties

import java.time.Duration

data class SubscriptionConfiguration(
    val partitionPollSize: Int,
    val threadStopTimeout: Duration,
)
