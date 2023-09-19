package net.corda.interop.identity.write

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.lifecycle.Lifecycle


interface InteropIdentityWriteService : Lifecycle {
    /**
     * Add a new interop identity from the view of a given holding virtual node.
     *
     * @param vNodeShortHash Short hash of the virtual node to add the interop identity to.
     * @param identity The new interop identity to add.
     */
    fun addInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity)

    /**
     * Remove interop identity from the view of a given holding identity.
     *
     * @param vNodeShortHash Short hash of the virtual node to remove the interop identity from.
     * @param identity Interop identity to remove.
     */
    fun removeInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity)

    /**
     * Add a new group policy json.
     *
     * @param groupId groupId of the interop group.
     * @param groupPolicy group policy content.
     */
    fun publishGroupPolicy(groupId: String, groupPolicy: String) : String
}
