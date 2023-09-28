package net.corda.membership.client

import net.corda.crypto.core.ShortHash
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.v5.base.types.MemberX500Name
import java.time.Instant
import java.util.UUID
import kotlin.jvm.Throws

/**
 * The MGM ops client to perform group operations.
 */
@Suppress("TooManyFunctions")
interface MGMResourceClient : Lifecycle {

    /**
     * Generates the Group Policy file to be used to register a new member to the MGM
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     *
     * @return [String] Generated Group Policy Response.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class)
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
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [MembershipPersistenceException] If an identical rule already exists.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class, MembershipPersistenceException::class)
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
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class)
    fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType): Collection<ApprovalRuleDetails>

    /**
     * Deletes a previously added approval rule.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param ruleId ID of the group approval rule to be deleted.
     * @param ruleType The approval rule type for this rule. See [ApprovalRuleType] for the available types.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [MembershipPersistenceException] If the specified rule does not exist.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class, MembershipPersistenceException::class)
    fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType)

    /**
     * Retrieves registration requests submitted to the MGM which are pending review, optionally filtered by the X.500
     * name of the requesting member, and/or by the status of the request (historic or pending review).
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param requestSubjectX500Name Optional. X.500 name of the subject of the registration request.
     * @param viewHistoric Optional. Set this to 'true' to view both pending review and completed (historic) requests.
     * Defaults to 'false' (requests pending review only).
     *
     * @return Registration requests as a collection of [RegistrationRequestDetails].
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class)
    fun viewRegistrationRequests(
        holdingIdentityShortHash: ShortHash,
        requestSubjectX500Name: MemberX500Name?,
        viewHistoric: Boolean,
    ): Collection<RegistrationRequestDetails>

    /**
     * Approve or decline registration requests which require manual approval. This method can only be used for
     * requests that are in "PENDING_MANUAL_APPROVAL" status.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param requestId ID of the registration request.
     * @param approve Set to 'true' if request is approved, 'false' if declined.
     * @param reason Reason if registration request is declined.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [IllegalArgumentException] If request is not found, if request is not pending review, or if member name
     * is missing from the context.
     * @throws [ContextDeserializationException] If request cannot be deserialized.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class, IllegalArgumentException::class)
    fun reviewRegistrationRequest(
        holdingIdentityShortHash: ShortHash,
        requestId: UUID,
        approve: Boolean,
        reason: String? = null,
    )

    /**
     * Force decline an in-progress registration request that may be stuck or displaying some other unexpected
     * behaviour. This method should only be used under exceptional circumstances.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param requestId ID of the registration request.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [IllegalArgumentException] If the request is not found, or has already been approved/declined.
     */
    @Throws(CouldNotFindEntityException::class, MemberNotAnMgmException::class, IllegalArgumentException::class)
    fun forceDeclineRegistrationRequest(
        holdingIdentityShortHash: ShortHash,
        requestId: UUID,
    )

    /**
     * Suspends a member.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param memberX500Name X.500 name of the member being suspended.
     * @param serialNumber Optional. Serial number of the member's [MemberInfo].
     * @param reason Optional. Reason for suspension.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [IllegalArgumentException] If the member to be suspended is the MGM itself.
     * @throws [NoSuchElementException] If the member to be suspended is not found.
     */
    @Throws(
        CouldNotFindEntityException::class,
        MemberNotAnMgmException::class,
        IllegalArgumentException::class,
        NoSuchElementException::class
    )
    fun suspendMember(
        holdingIdentityShortHash: ShortHash,
        memberX500Name: MemberX500Name,
        serialNumber: Long? = null,
        reason: String? = null,
    )

    /**
     * Activates a previously suspended member.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param memberX500Name X.500 name of the member being activated.
     * @param serialNumber Optional. Serial number of the member's [MemberInfo].
     * @param reason Optional. Reason for activation.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     * @throws [IllegalArgumentException] If the member to be activated is the MGM itself.
     * @throws [NoSuchElementException] If the member to be activated is not found.
     */
    @Throws(
        CouldNotFindEntityException::class,
        MemberNotAnMgmException::class,
        IllegalArgumentException::class,
        NoSuchElementException::class
    )
    fun activateMember(
        holdingIdentityShortHash: ShortHash,
        memberX500Name: MemberX500Name,
        serialNumber: Long? = null,
        reason: String? = null,
    )

    /**
     * Updates the group parameters.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param newGroupParameters Updated version of the group parameters.
     *
     * @throws [CouldNotFindEntityException] If there is no member or virtual node with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member identified by [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(
        CouldNotFindEntityException::class,
        MemberNotAnMgmException::class,
    )
    fun updateGroupParameters(
        holdingIdentityShortHash: ShortHash,
        newGroupParameters: Map<String, String>
    ): InternalGroupParameters
}