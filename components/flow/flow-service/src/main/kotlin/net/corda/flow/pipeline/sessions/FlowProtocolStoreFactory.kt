package net.corda.flow.pipeline.sessions

import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.sandbox.SandboxGroup

/**
 * Builds a [FlowProtocolStore] for a given sandbox.
 */
interface FlowProtocolStoreFactory {

    /**
     * Create a new [FlowProtocolStore].
     *
     * @param sandboxGroup The sandbox to create the flow protocol store for.
     * @return FlowProtocolStore.
     */
    fun create(sandboxGroup: SandboxGroup) : FlowProtocolStore
}