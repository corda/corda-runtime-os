package net.corda.ledger.verification.exceptions

/**
 * Indicates that a serialized set of results will exceed the size of a Kafka packet.
 *
 * Will be removed in a future version when we handle large result sets.
 */
class KafkaMessageSizeException(message: String, cause: Throwable? = null) : Exception(message, cause)
