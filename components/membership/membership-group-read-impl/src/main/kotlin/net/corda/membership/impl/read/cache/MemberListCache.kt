package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.util.Collections

/**
 * Interface for storing the member lists in-memory including implementation class.
 */
interface MemberListCache : MemberDataListCache<MemberInfo> {
    /**
     * Overload function allowing a single [MemberInfo] to be cached instead of a whole list.
     *
     * @param holdingIdentity The [HoldingIdentity] of the member whose view on data the caller is updating
     * @param data The data to cache
     */
    fun put(holdingIdentity: HoldingIdentity, data: MemberInfo) =
        put(holdingIdentity, listOf(data))

    /**
     * Simple in-memory member list cache implementation.
     */
    class Impl : MemberListCache {

        private val cache: MutableMap<String, MutableList<MemberInfo>> = Collections.synchronizedMap(mutableMapOf())

        private fun getMemberList(holdingIdentity: HoldingIdentity) =
            cache.getOrPut(holdingIdentity.id) {
                Collections.synchronizedList(mutableListOf())
            }

        override fun get(holdingIdentity: HoldingIdentity) = getMemberList(holdingIdentity)

        override fun put(holdingIdentity: HoldingIdentity, data: List<MemberInfo>) {
            with(getMemberList(holdingIdentity)) {
                removeIf { cached -> data.any { it.name == cached.name } }
                addAll(data)
            }
        }
    }
}