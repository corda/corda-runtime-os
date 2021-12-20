package net.corda.membership.impl.read.reader

import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent

/**
 * Factory for creating [MembershipGroupReader] for a holding identity (group ID & MemberX500Name).
 */
interface MembershipGroupReaderFactory {
    /**
     * Returns a group information service providing group information for the
     * specified group as viewed by the specified member.
     *
     * @param groupId String containing the group identifier.
     * @param memberX500Name MemberX500Name of the member requesting the group policy.
     */
    fun getGroupReader(groupId: String, memberX500Name: MemberX500Name): MembershipGroupReader

    /**
     * Default implementation.
     */
    class Impl(
        private val virtualNodeInfoReader: VirtualNodeInfoReaderComponent,
        private val cpiInfoReader: CpiInfoReaderComponent,
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
         */
        private fun getGroupPolicy(
            groupId: String,
            memberX500Name: MemberX500Name
        ): GroupPolicy {
            val holdingIdentity = HoldingIdentity(groupId, memberX500Name.toString())
            val groupPolicyJson = virtualNodeInfoReader.get(holdingIdentity)
                ?.cpi
                ?.let { cpiInfoReader.get(it)?.groupPolicy }
            requireNotNull(groupPolicyJson)
            return parseGroupPolicy(groupPolicyJson)
        }

        private fun parseGroupPolicy(groupPolicyJson: String): GroupPolicy {
            // Add group policy parsing call here. Can remove `require` function. Only added to temporarily satisfy
            // compilation.
            require(groupPolicyJson.length >= 0)
            return GroupPolicyImpl(emptyMap())
        }
    }
}
