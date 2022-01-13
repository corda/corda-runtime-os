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
        companion object {
            const val UNINITIALIZED_CACHE_ERROR = "Could not access the %s because the cache has not been " +
                    "initialised yet."
            const val MEMBER_LIST_CACHE = "member list cache"
            const val GROUP_READER_CACHE = "group reader cache"
        }

        override val memberListCache
            get() = _memberListCache ?: throw CordaRuntimeException(
                String.format(UNINITIALIZED_CACHE_ERROR, MEMBER_LIST_CACHE)
            )

        override val groupReaderCache
            get() = _groupReaderCache ?: throw CordaRuntimeException(
                String.format(UNINITIALIZED_CACHE_ERROR, GROUP_READER_CACHE)
            )

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