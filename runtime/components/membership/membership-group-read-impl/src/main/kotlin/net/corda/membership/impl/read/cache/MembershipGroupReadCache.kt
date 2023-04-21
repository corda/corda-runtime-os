package net.corda.membership.impl.read.cache

import net.corda.membership.read.MembershipGroupReader
import org.slf4j.LoggerFactory

/**
 * Interface wrapping all caches required for the membership group read service component.
 */
interface MembershipGroupReadCache {
    /**
     * Cache for member list data. This data is specific member views on data. Could contain views on member data for
     * different members across different groups or in the same group.
     */
    val memberListCache: MemberListCache

    /**
     * Group Reader cache for storing [MembershipGroupReader] instances that have already been created for faster
     * lookups.
     */
    val groupReaderCache: MemberDataCache<MembershipGroupReader>

    /**
     * Clears all cached data.
     */
    fun clear()

    /**
     * Default implementation of [MembershipGroupReadCache].
     */
    class Impl : MembershipGroupReadCache {
        companion object {
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        override var memberListCache: MemberListCache = MemberListCache.Impl()
        override var groupReaderCache: MemberDataCache<MembershipGroupReader> = MemberDataCache.Impl()

        private val caches = listOf(
            memberListCache,
            groupReaderCache
        )

        override fun clear() {
            logger.info("Clearing membership group read cache.")
            caches.forEach { it.clear() }
        }
    }
}