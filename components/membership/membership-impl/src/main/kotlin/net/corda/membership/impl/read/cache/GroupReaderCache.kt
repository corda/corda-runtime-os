package net.corda.membership.impl.read.cache

import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.identity.MemberX500Name

/**
 * Class for storing the member lists in-memory.
 */
class GroupReaderCache : MemberDataCache<MembershipGroupReader> {
    /**
     * Group reader in-memory cache.
     * Data is stored as a map from group ID to map of member name to group reader service.
     */
    private val cache: MutableMap<String, MutableMap<MemberX500Name, MembershipGroupReader>> = mutableMapOf()

    /**
     * Gets the latest map of group reader services for a group which can be updated or read from depending on the use
     * case. This is a private function since it allows modification of the map.
     */
    private fun getGroupReaders(groupId: String): MutableMap<MemberX500Name, MembershipGroupReader> =
        cache.getOrPut(groupId) { mutableMapOf() }

    override fun get(groupId: String, memberX500Name: MemberX500Name): MembershipGroupReader? =
        getGroupReaders(groupId)[memberX500Name]

    override fun put(groupId: String, memberX500Name: MemberX500Name, data: MembershipGroupReader) {
        getGroupReaders(groupId)[memberX500Name] = data
    }

    override fun clear() = cache.clear()
}