package net.corda.flow.pipeline.sandbox

import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

interface FlowSandboxService {

//TODO kdoc here to explain

    fun get(holdingIdentity: HoldingIdentity, cpks: Collection<SecureHash>): FlowSandboxGroupContext
}
