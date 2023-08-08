package net.corda.interop.identity.cache

import net.corda.virtualnode.HoldingIdentity
import net.corda.lifecycle.Lifecycle


interface InteropIdentityRegistryService : Lifecycle {
    /**
     * Returns an object representing a given virtual nodes view of the registry.
     *
     * @param virtualNodeShortHash Short hash of the virtual node to get the view for.
     * @return An object containing interop identities visible to the specified virtual node.
     */
    fun getVirtualNodeRegistryView(virtualNodeShortHash: String): InteropIdentityRegistryView

    /**
     * Returns an object representing a given virtual nodes view of the registry.
     *
     * @param holdingIdentity The Holding Identity of the Virtual Node to get the view for.
     * @return An object containing interop identities visible to the specified virtual node.
     */
    fun getVirtualNodeCacheView(holdingIdentity: HoldingIdentity): InteropIdentityRegistryView
}
