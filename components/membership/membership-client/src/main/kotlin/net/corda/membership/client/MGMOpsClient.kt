package net.corda.membership.client

import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import java.time.Instant
import java.util.UUID
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
     * Generate a preAuthToken.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM.
     * @param ownerX500Name The X500 name of the member to preauthorize.
     * @param ttl A (time-to-live) unix timestamp (in milliseconds) after which this token will become invalid. Defaults to infinity.
     * @param remarks Some optional remarks.
     */
    fun generatePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?,
    ): PreAuthToken

    /**
     * Query for preAuthTokens.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM.
     * @param ownerX500Name The X500 name of the member to query for.
     * @param preAuthTokenId The token ID to query for.
     * @param viewInactive Return in tokens with status [PreAuthTokenStatus.REVOKED], [PreAuthTokenStatus.CONSUMED],
     * [PreAuthTokenStatus.AUTO_INVALIDATED] as well as [PreAuthTokenStatus.AVAILABLE].
     */
    fun getPreAuthTokens(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean
    ): Collection<PreAuthToken>

    /**
     * Revoke a preAuthToken.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM.
     * @param preAuthTokenId The token ID to revoke.
     * @param remarks Some optional remarks about why the token was revoked.
     */
    fun revokePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        preAuthTokenId: UUID,
        remarks: String?
    ): PreAuthToken

    /**
     * Adds an approval rule to be applied to future registration requests.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param ruleParams Parameters of the rule to be added, represented by [ApprovalRuleParams].
     *
     * @return Details of the newly persisted approval rule.
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [MembershipPersistenceException] If an identical rule already exists.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class, MembershipPersistenceException::class)
    fun addApprovalRule(
        holdingIdentityShortHash: ShortHash,
        ruleParams: ApprovalRuleParams
    ): ApprovalRuleDetails

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
     * @param ruleType The approval rule type for this rule. See [ApprovalRuleType] for the available types.
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [MembershipPersistenceException] If the specified rule does not exist.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class, MembershipPersistenceException::class)
    fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType)

    /**
     * Retrieves all registration requests submitted to the MGM, optionally filtered by the X.500 name of the
     * requesting member, and/or by the status of the request (historic or in-progress).
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param requestingMemberX500Name Optional. X.500 name of the requesting member.
     * @param viewHistoric Optional. Set this to 'true' to view both in-progress and completed (historic) requests.
     * Defaults to 'false' (in-progress requests only).
     *
     * @return Registration requests as a collection of [RegistrationRequestStatus].
     *
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class)
    fun viewRegistrationRequests(
        holdingIdentityShortHash: ShortHash,
        requestingMemberX500Name: String?,
        viewHistoric: Boolean,
    ): Collection<RegistrationRequestStatus>
}