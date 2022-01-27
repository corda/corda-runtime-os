package net.corda.messagebus.api.consumer;

/**
 * Backup strategy for topic reset when no previous position on the message bus exists.
 * - [LATEST] - reset to the most recent offset position
 * - [EARLIEST] - reset to the earliest offset position
 * - [NONE] - throw an exception
 */
enum class CordaOffsetResetStrategy {
    LATEST, EARLIEST, NONE
}
