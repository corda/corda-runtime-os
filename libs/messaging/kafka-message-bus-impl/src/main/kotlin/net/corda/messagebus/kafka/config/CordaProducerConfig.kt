package net.corda.messagebus.kafka.config

import java.time.Duration

data class CordaProducerConfig(
    val clientId: String,
    val transactionalId: String?,
    val topicPrefix: String,
    val closeTimeout: Duration
)
