package net.corda.flow.application.versioning

import net.corda.v5.application.flows.SubFlow

/**
 * [VersionedFlowFactory] a common interface between [VersionedSendFlowFactory] and [VersionedReceiveFlowFactory].
 */
interface VersionedFlowFactory<T> {

    /**
     * Gets the flow that is being versioned.
     *
     * Used for logging.
     */
    val versionedInstanceOf: Class<out SubFlow<T>>
}