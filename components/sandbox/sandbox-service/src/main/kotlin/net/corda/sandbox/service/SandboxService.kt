package net.corda.sandbox.service

import net.corda.packaging.CPI
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.SandboxGroupContextInitializer
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.virtualnode.HoldingIdentity

/**
 * Sandbox group context service with an extra convenience method specifically for CPIs.
 */
interface SandboxService : SandboxGroupContextComponent {
    fun getOrCreateByCpiIdentifier(
        holdingIdentity: HoldingIdentity,
        cpiIdentifier: CPI.Identifier,
        sandboxGroupType: SandboxGroupType,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext
}
