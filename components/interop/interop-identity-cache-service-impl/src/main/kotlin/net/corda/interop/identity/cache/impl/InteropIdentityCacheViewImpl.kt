package net.corda.interop.identity.cache.impl

import java.util.Collections
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.cache.InteropIdentityCacheView


class InteropIdentityCacheViewImpl(private val holdingIdentityShortHash: String): InteropIdentityCacheView {
    private val interopIdentities = HashSet<InteropIdentity>()

    private val byGroupId = HashMap<String, HashSet<InteropIdentity>>()
    private val byHoldingIdentity = HashMap<String, HashSet<InteropIdentity>>()
    private val byShortHash = HashMap<String, HashSet<InteropIdentity>>()

    private fun getOrCreateByGroupIdEntry(groupId: String): HashSet<InteropIdentity> {
        return byGroupId.computeIfAbsent(groupId) {
            HashSet()
        }
    }

    private fun getOrCreateByHoldingIdentityEntry(shortHash: String): HashSet<InteropIdentity> {
        return byHoldingIdentity.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    private fun getOrCreateByShortHashIdentity(shortHash: String): HashSet<InteropIdentity> {
        return byShortHash.computeIfAbsent(shortHash) {
            HashSet()
        }
    }

    fun putInteropIdentity(identity: InteropIdentity) {
        interopIdentities.add(identity)
        getOrCreateByGroupIdEntry(identity.groupId).add(identity)
        getOrCreateByHoldingIdentityEntry(identity.holdingIdentityShortHash).add(identity)
        getOrCreateByShortHashIdentity(identity.shortHash).add(identity)
    }

    fun removeInteropIdentity(identity: InteropIdentity) {
        interopIdentities.remove(identity)

        byGroupId[identity.groupId]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.groupId)
            }
        }

        byHoldingIdentity[identity.holdingIdentityShortHash]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byGroupId.remove(identity.holdingIdentityShortHash)
            }
        }

        byShortHash[identity.shortHash]?.let {
            it.remove(identity)
            if (it.size == 0) {
                byShortHash.remove(identity.shortHash)
            }
        }
    }

    override fun getIdentities(): Set<InteropIdentity> = interopIdentities

    override fun getIdentitiesByGroupId(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byGroupId)

    override fun getIdentitiesByHoldingIdentity(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byHoldingIdentity)

    override fun getIdentitiesByShortHash(): Map<String, Set<InteropIdentity>> =
        Collections.unmodifiableMap(byShortHash)
}
