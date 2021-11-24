package net.corda.flow.manager

import net.corda.packaging.CPI
import net.corda.virtual.node.context.HoldingIdentity
import net.corda.virtual.node.sandboxgroup.SandboxGroupContext

interface FlowSandboxService{
    fun get(
        holdingIdentity: HoldingIdentity,
        cpi: CPI.Identifier,
    ): SandboxGroupContext
}