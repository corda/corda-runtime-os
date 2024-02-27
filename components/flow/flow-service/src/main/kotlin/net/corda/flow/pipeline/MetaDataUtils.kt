package net.corda.flow.pipeline

import net.corda.flow.state.impl.CheckpointMetadataKeys
import net.corda.libs.statemanager.api.Metadata

/**
 * Adds termination key to the Checkpoint states MetaData
 */
fun addTerminationKeyToMeta(metaData: Metadata?): Metadata {
    val newMeta = mapOf(CheckpointMetadataKeys.STATE_META_CHECKPOINT_TERMINATED_KEY to true)
    return metaData?.let {
        Metadata(it + newMeta)
    } ?: Metadata(newMeta)
}
