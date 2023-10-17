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
}