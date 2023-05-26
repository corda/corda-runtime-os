package net.corda.sandboxgroupcontext

import net.corda.virtualnode.HoldingIdentity

interface SandboxedCache {

    data class CacheKey<T>(val holdingIdentity: HoldingIdentity, val sandboxGroupType: SandboxGroupType, val key: T) {

        constructor(virtualNodeContext: VirtualNodeContext, key: T) : this(
            virtualNodeContext.holdingIdentity,
            virtualNodeContext.sandboxGroupType,
            key
        )
    }

    fun remove(holdingIdentity: HoldingIdentity, sandboxGroupType: SandboxGroupType)

}