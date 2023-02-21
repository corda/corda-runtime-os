package net.corda.flow.pipeline.sandbox

import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

interface FlowSandboxService {

/**
 * Uses the sandbox service to return a sandbox with extra services required for the flow pipeline
 */

/**
 * @return A flow sandbox
 */

    fun get(holdingIdentity: HoldingIdentity, cpks: Collection<SecureHash>): FlowSandboxGroupContext
}
