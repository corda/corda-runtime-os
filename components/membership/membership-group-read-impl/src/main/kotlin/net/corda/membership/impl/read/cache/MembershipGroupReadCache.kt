package net.corda.membership.impl.read.cache

import net.corda.lifecycle.Lifecycle
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Interface wrapping all caches required for the membership group read service component.
 */
interface MembershipGroupReadCache : Lifecycle {
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
     * Default implementation of [MembershipGroupReadCache].
     */
    class Impl : MembershipGroupReadCache {
        private companion object {
            const val UNINITIALIZED_CACHE_ERROR = "Could not check membership group data cache because the cache " +
                    "has not been initialised yet."
        }

        override val memberListCache
            get() = _memberListCache ?: throw CordaRuntimeException(UNINITIALIZED_CACHE_ERROR)

        override val groupReaderCache
            get() = _groupReaderCache ?: throw CordaRuntimeException(UNINITIALIZED_CACHE_ERROR)

        override var isRunning: Boolean = false

        private var _memberListCache: MemberListCache? = null
        private var _groupReaderCache: MemberDataCache<MembershipGroupReader>? = null

        override fun start() {
            _memberListCache = MemberListCache.Impl()
            _groupReaderCache = MemberDataCache.Impl()
            isRunning = true
        }

        override fun stop() {
            isRunning = false
            _memberListCache = null
            _groupReaderCache = null
        }
    }
}