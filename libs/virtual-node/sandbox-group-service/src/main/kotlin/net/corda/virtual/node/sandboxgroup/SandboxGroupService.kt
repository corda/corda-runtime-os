package net.corda.virtual.node.sandboxgroup

import net.corda.packaging.CPI
import net.corda.sandbox.SandboxGroup
import net.corda.virtual.node.context.HoldingIdentity

interface SandboxGroupService {
    /**
     * This function returns a "fully constructed" node that is ready to
     * execute a flow with no further configuration required.
     *
     * Implicitly maps the [HoldingIdentity] to only ONE [CPI]
     *
     * This function should be implemented as a pure function, i.e.
     * if the "virtual node" already exists and is constructed, that
     * should be returned, and the [initializer] should not be run.
     */
    fun get(holdingIdentity: HoldingIdentity,
            cpi : CPI.Identifier,
            sandboxGroupType: SandboxGroupType,
            initializer: (holdingIdentity: HoldingIdentity, sandboxGroup: SandboxGroup) -> Unit
    ) : SandboxGroupContext
}
