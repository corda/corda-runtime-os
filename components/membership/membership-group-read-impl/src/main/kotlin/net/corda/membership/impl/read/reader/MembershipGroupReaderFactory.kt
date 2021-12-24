package net.corda.membership.impl.read.reader

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.cipher.suite.KeyEncodingService
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
        private val keyEncodingService: KeyEncodingService
    ) : MembershipGroupReaderFactory {
        private val groupReaderCache get() = membershipGroupReadCache.groupReaderCache

        override fun getGroupReader(holdingIdentity: HoldingIdentity) =
            groupReaderCache.get(holdingIdentity)
                ?: createGroupReader(holdingIdentity)

        private fun createGroupReader(
            holdingIdentity: HoldingIdentity
        ) = MembershipGroupReaderImpl(
            holdingIdentity,
            getGroupPolicy(holdingIdentity),
            membershipGroupReadCache,
            keyEncodingService
        ).apply {
            groupReaderCache.put(holdingIdentity, this)
        }

        /**
         * Retrieves the GroupPolicy JSON string from the CPI metadata and parses it into a [GroupPolicy] object.
         * Stub until actual implementation exists.
         */
        private fun getGroupPolicy(
            holdingIdentity: HoldingIdentity
        ): GroupPolicy {
            return GroupPolicyImpl(
                mapOf(
                    "groupId" to holdingIdentity.groupId,
                    "owningMember" to holdingIdentity.x500Name
                )
            )
        }
    }
}
