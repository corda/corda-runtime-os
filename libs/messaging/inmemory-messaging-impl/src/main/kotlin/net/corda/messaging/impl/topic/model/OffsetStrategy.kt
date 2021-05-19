package net.corda.messaging.impl.topic.model

/**
 * Offset strategy for consumer group position on topics.
 * [LATEST] will set offset to be the next new record on the topic.
 * [EARLIEST] will set offset to the oldest record on the topic.
 */
enum class OffsetStrategy {
    LATEST, EARLIEST
}
