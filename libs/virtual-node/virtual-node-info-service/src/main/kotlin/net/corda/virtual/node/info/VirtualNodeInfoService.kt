package net.corda.virtual.node.info

import net.corda.virtual.node.context.HoldingIdentity
import net.corda.virtual.node.context.VirtualNodeContext

interface VirtualNodeInfoService {
    /**
     * Returns virtual node context information for a given holding identity
     * without starting any bundles or instantiating any classes.
     *
     * Read-only and 'lightweight'.
     */
    fun get(holdingIdentity: HoldingIdentity) : VirtualNodeContext
}
