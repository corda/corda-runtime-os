package net.corda.sandboxgroupcontext

import net.corda.virtualnode.HoldingIdentity

interface SandboxedCache {

    fun remove(holdingIdentity: HoldingIdentity, sandboxGroupType: SandboxGroupType)
}