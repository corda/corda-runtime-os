package net.corda.messaging.subscription.consumer

import java.time.Duration

data class SimpleConsumerConfig(val topic:String, val pollTimeout: Duration)