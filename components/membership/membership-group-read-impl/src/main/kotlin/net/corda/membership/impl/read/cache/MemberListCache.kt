package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name
import java.util.Collections

/**
 * Interface for storing the member lists in-memory including implementation class.
 */
interface MemberListCache : MemberDataListCache<MemberInfo> {
    /**
     * Overload function allowing a single [MemberInfo] to be cached instead of a whole list.
     *
     * @param groupId The membership group ID as a [String].
     * @param memberX500Name The [MemberX500Name] of the member who owns the cached data.
     * @param data The data to cache
     */
    fun put(groupId: String, memberX500Name: MemberX500Name, data: MemberInfo) =
        put(groupId, memberX500Name, listOf(data))

    /**
     * Simple in-memory member list cache implementation.
     */
    class Impl : MemberListCache {

        private val cache: MutableMap<String, MutableMap<MemberX500Name, MutableList<MemberInfo>>> =
            Collections.synchronizedMap(mutableMapOf())

        private fun getMemberList(groupId: String, memberX500Name: MemberX500Name): MutableList<MemberInfo> =
            cache.getOrPut(groupId) {
                Collections.synchronizedMap(mutableMapOf())
            }.getOrPut(memberX500Name) {
                Collections.synchronizedList(mutableListOf())
            }

        override fun get(groupId: String, memberX500Name: MemberX500Name): List<MemberInfo> =
            getMemberList(groupId, memberX500Name)

        override fun put(groupId: String, memberX500Name: MemberX500Name, data: List<MemberInfo>) {
            with(getMemberList(groupId, memberX500Name)) {
                removeIf { cached -> data.any { it.name == cached.name } }
                addAll(data)
            }
        }
    }
}