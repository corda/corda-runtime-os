package net.corda.membership.grouppolicy

import net.corda.membership.GroupPolicy
import net.corda.v5.membership.identity.MemberX500Name

/**
 * Service for retrieving the group policy file for a given holding identity
 */
interface GroupPolicyProvider {

    /**
     * Retrieves the [GroupPolicy] object for a given member based on the member's holding identity.
     *
     * @param groupId The ID of the group the member doing the lookup belongs to.
     * @param memberX500Name The MemberX500Name of the member doing the lookup.
     * @return The current [GroupPolicy] file that was bundled with the CPI which was installed for the given holding
     *  identity.
     */
    fun getGroupPolicy(groupId: String, memberX500Name: MemberX500Name): GroupPolicy
}