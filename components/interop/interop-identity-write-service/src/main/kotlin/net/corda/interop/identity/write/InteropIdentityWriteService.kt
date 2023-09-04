package net.corda.interop.identity.write

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.lifecycle.Lifecycle
import java.util.*


interface InteropIdentityWriteService : Lifecycle {
    /**
     * Add a new interop identity for a given holding identity within a given interop group.
     *
     * @param vNodeShortHash Short hash of the virtual node to add the interop identity to.
     * @param identity The new interop identity to add.
     */
    fun addInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity)

    /**
     * Add a new group policy json.
     *
     * @param groupId groupId of the interop group.
     * @param groupPolicy group policy content.
     */
    fun publishGroupPolicy(groupId: UUID, groupPolicy: String) : UUID
}
