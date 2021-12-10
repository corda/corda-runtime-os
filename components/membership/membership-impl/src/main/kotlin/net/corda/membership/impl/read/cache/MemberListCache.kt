package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name

/**
 * Class for storing the member lists in-memory.
 */
class MemberListCache : MemberDataListCache<MemberInfo> {

    /**
     * In-memory member list cache. Data is stored as a map from group ID to map of member name to list of visible members.
     */
    private val cache: MutableMap<String, MutableMap<MemberX500Name, MutableList<MemberInfo>>> = mutableMapOf()

    /**
     * Gets the latest member list for a holding identity.
     * This is a private function because it finds the mutable list.
     */
    private fun getMemberList(groupId: String, memberX500Name: MemberX500Name): MutableList<MemberInfo> =
        cache.getOrPut(groupId) {
            mutableMapOf()
        }.getOrPut(memberX500Name) {
            mutableListOf()
        }

    override fun get(groupId: String, memberX500Name: MemberX500Name): List<MemberInfo> =
        getMemberList(groupId, memberX500Name)

    override fun put(groupId: String, memberX500Name: MemberX500Name, data: List<MemberInfo>) {
        with(getMemberList(groupId, memberX500Name)) {
            removeIf { cached -> data.any { it.name == cached.name } }
            addAll(data)
        }
    }

    override fun clear() = cache.clear()
}