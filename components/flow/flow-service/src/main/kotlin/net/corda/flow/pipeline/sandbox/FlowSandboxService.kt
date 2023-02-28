package net.corda.flow.pipeline.sandbox

import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

/**
 * Uses the sandbox service to return a sandbox with extra services required for the flow pipeline.
 */

interface FlowSandboxService {

    /**
     * Uses the sandbox service to return a sandbox with extra services required for the flow pipeline.
     *
     * @param holdingIdentity a HoldingIdentity.
     * @param cpkFileHashes a Collection of cpk FileHashes.
     * @return a FlowSandboxGroupContext.
     */

    fun get(holdingIdentity: HoldingIdentity, cpkFileHashes: Set<SecureHash>): FlowSandboxGroupContext
}
