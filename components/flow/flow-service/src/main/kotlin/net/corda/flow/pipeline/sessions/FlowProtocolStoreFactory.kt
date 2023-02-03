package net.corda.flow.pipeline.sessions

import net.corda.sandbox.SandboxGroup

/**
 * Builds a [FlowProtocolStore] for a given sandbox.
 */
interface FlowProtocolStoreFactory {

    /**
     * Create a new [FlowProtocolStore].
     *
     * @param sandboxGroup The sandbox to create the flow protocol store for.
     * @param cpiMetadata CPI metadata for the CPI installed into this sandbox.
     */
    fun create(sandboxGroup: SandboxGroup) : FlowProtocolStore
}