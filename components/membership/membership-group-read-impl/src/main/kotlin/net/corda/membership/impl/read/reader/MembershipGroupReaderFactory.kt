package net.corda.membership.impl.read.reader

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.identity.MemberX500Name

/**
 * Factory for creating [MembershipGroupReader] for a holding identity (group ID & MemberX500Name).
 */
interface MembershipGroupReaderFactory {
    /**
     * Returns a group information service providing group information for the
     * specified group as viewed by the specified member.
     *
     * @param groupId The group identifier on the group the caller is requesting a view on.
     * @param memberX500Name [MemberX500Name] of the member whose view on the group the caller is requesting.
     */
    fun getGroupReader(groupId: String, memberX500Name: MemberX500Name): MembershipGroupReader

    /**
     * Default implementation.
     */
    class Impl(
        private val membershipGroupReadCache: MembershipGroupReadCache,
    ) : MembershipGroupReaderFactory {
        private val groupReaderCache get() = membershipGroupReadCache.groupReaderCache

        override fun getGroupReader(groupId: String, memberX500Name: MemberX500Name) =
            groupReaderCache.get(groupId, memberX500Name)
                ?: createGroupReader(groupId, memberX500Name)

        private fun createGroupReader(
            groupId: String,
            memberX500Name: MemberX500Name
        ) = MembershipGroupReaderImpl(
            groupId,
            memberX500Name,
            getGroupPolicy(groupId, memberX500Name),
            membershipGroupReadCache
        ).apply {
            groupReaderCache.put(groupId, memberX500Name, this)
        }

        /**
         * Retrieves the GroupPolicy JSON string from the CPI metadata and parses it into a [GroupPolicy] object.
         * Stub until actual implementation exists.
         */
        private fun getGroupPolicy(
            groupId: String,
            memberX500Name: MemberX500Name
        ): GroupPolicy {
            return GroupPolicyImpl(
                mapOf(
                    "groupId" to groupId,
                    "owningMember" to memberX500Name
                )
            )
        }
    }
}
