package net.corda.messaging.api.constants

/**
 * Keys that the messaging library will apply to state metadata in some circumstances.
 */

object MessagingMetadataKeys {
    /**
     * Indicates that message processing failed for this state.
     *
     * Clients may wish to perform cleanup on states that have failed message processing. For those that wish to do so, a
     * lookup of states with this key set should be performed. The existence of this key in the metadata indicates that
     * processing of the state failed.
     */
    const val FAILED_STATE = "messaging-state-failure"
}