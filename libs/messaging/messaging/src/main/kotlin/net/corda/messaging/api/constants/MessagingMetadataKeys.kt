package net.corda.messaging.api.constants

import net.corda.libs.statemanager.api.State

/**
 * Keys, stored within the [State.metadata], that the messaging library will apply in some circumstances. These keys are
 * managed by messaging layer, but can be used as a mean of communicating certain conditions to the message processors.
 */
object MessagingMetadataKeys {

    /**
     * Indicates that message processing failed for this state.
     *
     * Clients may wish to perform cleanup on states that have failed message processing. For those that wish to do so,
     * a lookup of states with this key set should be performed. The existence of this key in the metadata with value
     * 'true' indicates that processing of the state failed.
     */
    const val PROCESSING_FAILURE = "processing.failure"
}
