package net.corda.membership.impl.read.cache

import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

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

        companion object {
            val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        private val cache = ConcurrentHashMap<HoldingIdentity, ReplaceableList<MemberInfo>>()

        override fun get(holdingIdentity: HoldingIdentity): List<MemberInfo> = cache[holdingIdentity] ?: emptyList()
        override fun getAll(): Map<HoldingIdentity, List<MemberInfo>> = cache

        override fun put(holdingIdentity: HoldingIdentity, data: List<MemberInfo>) {
            cache.compute(holdingIdentity) { _, value ->
                (value ?: ReplaceableList())
                    .addOrReplace(data) { old, new ->
                        old.name == new.name
                    }
            }
        }

        override fun clear() {
            logger.info("Clearing member list cache.")
            cache.clear()
        }

        /**
         * An implementation of [List] which has additional method [addOrReplace]. Calling [addOrReplace] will result in the
         * underlying list being overwritten rather than mutated.
         */
        private class ReplaceableList<T>(seed: List<T> = emptyList()) : List<T> {
            private var data: List<T> = seed

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
                ArrayList(
                    data.filter { o ->
                        candidates.all { n -> !predicate(o, n) }
                    }
                ).apply {
                    // Add all new candidates
                    addAll(candidates)
                    data = this
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
