package net.corda.flow.pipeline

import net.corda.libs.statemanager.api.Metadata

/**
 * Adds termination key to the Checkpoint states MetaData
 */
fun addTerminationKeyToMeta(metaData: Metadata?): Metadata {
    return metaData?.let {
        Metadata(it )
    } ?: Metadata(emptyMap())
}
