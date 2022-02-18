package net.corda.introspiciere.domain

data class KafkaMessage(
    val topic: String,
    val key: String,
    val schema: ByteArray,
    val schemaClass: String,
)
