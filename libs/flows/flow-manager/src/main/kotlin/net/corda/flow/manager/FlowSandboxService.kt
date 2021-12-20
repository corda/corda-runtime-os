package net.corda.flow.manager

import net.corda.packaging.CPI
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.sandboxgroup.SandboxGroupContext

interface FlowSandboxService {
    fun get(
        holdingIdentity: HoldingIdentity,
        cpi: CPI.Identifier,
    ): SandboxGroupContext
}