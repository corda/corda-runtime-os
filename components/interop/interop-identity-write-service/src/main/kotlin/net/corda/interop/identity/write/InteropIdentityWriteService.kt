package net.corda.interop.identity.write

import java.util.*
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
     * Update enablement state of interop identity.
     *
     * @param vNodeShortHash Short hash of the virtual node to enable/disable an interop identity of.
     * @param identity interop identity to enable/disable.
     * @param enablementState State to set enablement to. True to enable, false to disable.
     */
    fun updateInteropIdentityEnablement(vNodeShortHash: ShortHash, identity: InteropIdentity, enablementState: Boolean)

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
     * @param groupPolicy group policy to publish.
     * @return The UUID of the created group, either generated or taken from the [groupPolicy] argument.
     */
    fun publishGroupPolicy(groupPolicy: String) : UUID
}
