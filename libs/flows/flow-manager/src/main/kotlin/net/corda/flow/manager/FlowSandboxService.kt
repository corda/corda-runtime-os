package net.corda.flow.manager

import net.corda.packaging.CPI
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.virtualnode.HoldingIdentity

interface FlowSandboxService {
    fun get(
        holdingIdentity: HoldingIdentity,
        cpi: CPI.Identifier,
    ): SandboxGroupContext
}
