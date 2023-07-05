package net.corda.interop.identity.cache

import net.corda.data.interop.InteropIdentity


/**
 * This class represents the virtual-node local state of interop identities known to a specific holding identity.
 */
class InteropIdentityCacheView(private val holdingIdentityShortHash: String) {
    private val interopIdentities = HashSet<InteropIdentityCacheEntry>()
    private var myInteropIdentity: InteropIdentityCacheEntry? = null

    private val byGroupId = HashMap<String, HashSet<InteropIdentityCacheEntry>>()


    private fun getGroupIdentities(groupId: String): HashSet<InteropIdentityCacheEntry> {
        if (!byGroupId.containsKey(groupId)) {
            byGroupId[groupId] = HashSet()
        }

        return byGroupId[groupId]!!
    }


    fun addIdentity(identity: InteropIdentityCacheEntry) {
        interopIdentities.add(identity)

        if (identity.holdingIdentityShortHash == holdingIdentityShortHash) {
            myInteropIdentity = identity
        }

        getGroupIdentities(identity.groupId).add(identity)
    }


    fun removeIdentity(identity: InteropIdentityCacheEntry) {
        interopIdentities.remove(identity)
        getGroupIdentities(identity.groupId).remove(identity)
    }


    fun getIdentities() = interopIdentities
    fun getIdentities(groupId: String) = getGroupIdentities(groupId)
}
