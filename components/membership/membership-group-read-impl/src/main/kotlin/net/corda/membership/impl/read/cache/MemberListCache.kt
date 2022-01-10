package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

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
     * In-memory member list cache implementation.
     */
    class Impl : MemberListCache {

        private val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()
        private val cache = mutableMapOf<String, MutableList<MemberInfo>>()

        private fun getMemberListOrNull(holdingIdentity: HoldingIdentity) = cache[holdingIdentity.id]

        private fun setMemberList(holdingIdentity: HoldingIdentity, data: MutableList<MemberInfo>) {
            cache[holdingIdentity.id] = data
        }

        override fun get(holdingIdentity: HoldingIdentity): List<MemberInfo> {
            with(readWriteLock.readLock()) {
                lock()
                return try {
                    MemberList(getMemberListOrNull(holdingIdentity) ?: emptyList())
                } finally {
                    unlock()
                }
            }
        }

        override fun put(holdingIdentity: HoldingIdentity, data: List<MemberInfo>) {
            with(readWriteLock.writeLock()) {
                lock()
                try {
                    var memberList = getMemberListOrNull(holdingIdentity)
                    if (memberList == null) {
                        memberList = mutableListOf()
                        setMemberList(holdingIdentity, memberList)
                    }
                    with(memberList) {
                        removeIf { cached -> data.any { it.name == cached.name } }
                        addAll(data)
                    }
                } finally {
                    unlock()
                }
            }
        }

        /**
         * Wrapper class to prevent clients casting to the underlying mutable list and changing cached data without
         * going through the [put] function which properly handles concurrent updates.
         */
        private class MemberList(list: List<MemberInfo>) : List<MemberInfo> by list
    }
}