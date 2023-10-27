package net.corda.membership.impl.read.reader

import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReader
import net.corda.virtualnode.HoldingIdentity

/**
 * Factory for creating [MembershipGroupReader] for a holding identity.
 */
interface MembershipGroupReaderFactory {
    /**
     * Returns a group information service providing group information for the
     * specified group as viewed by the specified member.
     *
     * @param holdingIdentity The [HoldingIdentity] of the member whose view on data the caller is requesting
     */
    fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader

    /**
     * Default implementation.
     */
    class Impl(
        private val membershipGroupReadCache: MembershipGroupReadCache,
        private val groupParametersReaderService: GroupParametersReaderService,
        private val memberInfoFactory: MemberInfoFactory,
        private val platformInfoProvider: PlatformInfoProvider,
    ) : MembershipGroupReaderFactory {
        private val groupReaderCache get() = membershipGroupReadCache.groupReaderCache

        override fun getGroupReader(holdingIdentity: HoldingIdentity) =
            groupReaderCache.get(holdingIdentity)
                ?: createGroupReader(holdingIdentity)

        private fun createGroupReader(
            holdingIdentity: HoldingIdentity
        ) = MembershipGroupReaderImpl(
            holdingIdentity,
            membershipGroupReadCache,
            groupParametersReaderService,
            memberInfoFactory,
            platformInfoProvider,
        ).apply {
            groupReaderCache.put(holdingIdentity, this)
        }
    }
}
