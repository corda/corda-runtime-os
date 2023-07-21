package net.corda.interop.identity.write

import net.corda.interop.core.InteropIdentity
import net.corda.lifecycle.Lifecycle


interface InteropIdentityWriteService : Lifecycle {
    /**
     * Add a new interop identity for a given holding identity within a given interop group.
     *
     * @param vNodeShortHash Short hash of the virtual node to add the interop identity to.
     * @param identity The new interop identity to add.
     */
    fun addInteropIdentity(vNodeShortHash: String, identity: InteropIdentity)

    /**
     * Add a new group policy json.
     *
     * @param groupId groupId of the interop group.
     * @param groupPolicy group policy content.
     */
    fun addGroupPolicy(groupId: String, groupPolicy: String)
}
