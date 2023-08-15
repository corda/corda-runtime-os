package net.corda.interop.identity.registry

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.Lifecycle


interface InteropIdentityRegistryService : Lifecycle {
    /**
     * Returns an object representing a given virtual nodes view of the registry.
     *
     * @param virtualNodeShortHash Short hash of the virtual node to get the view for.
     * @return An object containing interop identities visible to the specified virtual node.
     */
    fun getVirtualNodeRegistryView(virtualNodeShortHash: ShortHash): InteropIdentityRegistryView
}
