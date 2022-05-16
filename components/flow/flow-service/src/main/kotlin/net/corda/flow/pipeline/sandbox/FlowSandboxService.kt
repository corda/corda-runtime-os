package net.corda.flow.pipeline.sandbox

import net.corda.virtualnode.HoldingIdentity

interface FlowSandboxService {

    fun get(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext
}
