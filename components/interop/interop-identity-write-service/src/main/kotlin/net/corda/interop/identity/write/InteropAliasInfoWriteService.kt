package net.corda.interop.identity.write

import net.corda.lifecycle.Lifecycle


interface InteropAliasInfoWriteService : Lifecycle {
    /**
     * Add a new interop identity for a given holding identity within a given interop group.
     *
     * @param holdingIdentityShortHash Short hash of the holding identity that owns the new interop identity.
     * @param interopGroupId Group ID of the interop group to add the interop identity to.
     * @param newIdentityName X500 name for the new interop identity.
     */
    fun addInteropIdentity(
        holdingIdentityShortHash: String,
        interopGroupId: String,
        newIdentityName: String
    )
}
