package net.corda.membership.impl.read.cache

import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.metrics.CordaMetrics
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

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

        private companion object {
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        private val cache = ConcurrentHashMap<HoldingIdentity, ReplaceableList<MemberInfo>>()

        override fun get(holdingIdentity: HoldingIdentity): List<MemberInfo> = cache[holdingIdentity] ?: emptyList()
        override fun getAll(): Map<HoldingIdentity, List<MemberInfo>> = cache

        override fun put(holdingIdentity: HoldingIdentity, data: List<MemberInfo>) {
            cache.computeIfAbsent(holdingIdentity) { holdingId ->
                ReplaceableList<MemberInfo>(emptyList()).also { list ->
                    CordaMetrics.Metric.Membership.MemberListCacheSize(list).builder()
                        .forVirtualNode(holdingId.shortHash.value)
                        .withTag(CordaMetrics.Tag.MembershipGroup, holdingId.groupId)
                        .build()
                }
            }.addOrReplace(data) { old, new ->
                if (new.status == MEMBER_STATUS_PENDING) {
                    old.status == MEMBER_STATUS_PENDING && old.name == new.name
                } else {
                    old.status != MEMBER_STATUS_PENDING && old.name == new.name
                }
            }
        }

        override fun clear() {
            logger.info("Clearing member list cache.")
            val lookup = CordaMetrics.Metric.Membership.MemberListCacheSize(null).builder()
            cache.keys.forEach { hid ->
                // Delete the cache's metrics from the registry.
                val id = lookup.resetTags()
                    .forVirtualNode(hid.shortHash.value)
                    .withTag(CordaMetrics.Tag.MembershipGroup, hid.groupId)
                    .buildPreFilterId()
                CordaMetrics.registry.removeByPreFilterId(id)
            }
            cache.clear()
        }

        /**
         * An implementation of [List] which has additional method [addOrReplace]. Calling [addOrReplace] will result in the
         * underlying list being overwritten rather than mutated.
         */
        private class ReplaceableList<T>(seed: List<T>) : List<T> {
            private val dataRef = AtomicReference(seed)
            private var data: List<T>
                set(value) = dataRef.set(value)
                get() = dataRef.get()

            /**
             * Update the underlying list to include the given list of candidates. All candidates in the given list will
             * be added, and if any existing entries match the given predicate when compared to the given candidate list, they
             * will not be included in the new list.
             * The underlying list is replaced instead of mutated.
             *
             * @param candidates list of new entries to add to the list (possibly replacing existing entries)
             * @param predicate the function used to detect when to replace instead of add. When an existing entry paired with
             * any of the candidates is true for the given predicate, then that entry is replaced.
             */
            fun addOrReplace(candidates: List<T>, predicate: (oldEntry: T, newEntry: T) -> Boolean): ReplaceableList<T> {
                // Add all items which do not match one of the new candidates from the old list to the new list
                data.filterTo(arrayListOf()) { o ->
                    candidates.none { n -> predicate(o, n) }
                }.also { newData ->
                    // Add all new candidates
                    newData.addAll(candidates)
                    data = newData
                }
                return this
            }

            override val size get() = data.size

            override fun contains(element: T) = data.contains(element)

            override fun containsAll(elements: Collection<T>) = data.containsAll(elements)

            override fun get(index: Int) = data[index]

            override fun indexOf(element: T) = data.indexOf(element)

            override fun isEmpty() = data.isEmpty()

            override fun iterator() = data.iterator()

            override fun lastIndexOf(element: T) = data.lastIndexOf(element)

            override fun listIterator() = data.listIterator()

            override fun listIterator(index: Int) = data.listIterator(index)

            override fun subList(fromIndex: Int, toIndex: Int) = data.subList(fromIndex, toIndex)
        }
    }
}
