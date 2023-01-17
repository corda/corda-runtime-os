package net.corda.membership.client

import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import kotlin.jvm.Throws

/**
 * The MGM ops client to perform group operations.
 */
interface MGMOpsClient : Lifecycle {

    /**
     * Generates the Group Policy file to be used to register a new member to the MGM
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     *
     * @return [String] Generated Group Policy Response.
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class)
    fun generateGroupPolicy(holdingIdentityShortHash: ShortHash): String

    fun mutualTlsAllowClientCertificate(
        holdingIdentityShortHash: ShortHash,
        subject: MemberX500Name,
    )
    fun mutualTlsDisallowClientCertificate(
        holdingIdentityShortHash: ShortHash,
        subject: MemberX500Name,
    )
    fun mutualTlsListClientCertificate(
        holdingIdentityShortHash: ShortHash,
    ): Collection<MemberX500Name>


    /**
     * Adds an approval rule of the specified [ruleType] to be applied to future registration requests.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param rule The regular expression associated with the rule to be added.
     * @param ruleType The approval rule type for this rule. See [ApprovalRuleType] for the available types.
     * @param label Optional. A label describing the rule to be added.
     *
     * @return The ID of the newly added rule.
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [MembershipPersistenceException] If an identical rule already exists.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class, MembershipPersistenceException::class)
    fun addApprovalRule(
        holdingIdentityShortHash: ShortHash,
        rule: String,
        ruleType: ApprovalRuleType,
        label: String? = null
    ): String

    /**
     * Retrieves all approval rules of the specified [ruleType] currently configured for the group.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param ruleType The approval rule type for this rule. See [ApprovalRuleType] for the available types.
     *
     * @return Approval rules as a collection of [ApprovalRuleDetails], or an empty collection if no rules have been
     * added.
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class)
    fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType): Collection<ApprovalRuleDetails>

    /**
     * Deletes a previously added approval rule.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param ruleId ID of the group approval rule to be deleted.
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [MembershipPersistenceException] If the specified rule does not exist.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class, MembershipPersistenceException::class)
    fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String)
}