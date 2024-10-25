package net.corda.flow.state.impl

/**
 * Metadata keys for information stored alongside the flow checkpoint.
 */
object CheckpointMetadataKeys {
    /**
     * Earliest expiry time of any session still active in this checkpoint.
     *
     * Note that the time provided here should only take into consideration open sessions. If the checkpoint has no open
     * sessions, then this metadata key should be removed.
     */
    const val STATE_META_SESSION_EXPIRY_KEY = "session.expiry"

    /**
     * When set to true, this key signals that a checkpoint has reached its termination state and can be deleted.
     * Checkpoints will be deleted by a cleanup processor based on a configurable time/
     */
    const val STATE_META_CHECKPOINT_TERMINATED_KEY = "checkpoint.terminated"

    /**
     * Records how long to retry external events that suffered transient errors in the message pattern.
     * These retry events originate from the message pattern and are not related to external event responses with error of type TRANSIENT
     */
    const val RETRY_EXPIRY = "retry.expiry"
}