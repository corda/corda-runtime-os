package net.corda.introspiciere.payloads

data class KafkaMessagesBatch(
    val schemaClass: String,
    val messages: List<ByteArray>,
    val latestOffsets: LongArray
)