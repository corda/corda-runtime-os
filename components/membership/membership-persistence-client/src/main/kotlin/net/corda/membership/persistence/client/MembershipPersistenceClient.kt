package net.corda.membership.persistence.client

import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant
import java.util.UUID

/**
 * Interface to be implemented by the service which requires membership persistence outside of the DB worker.
 * This client handles persistence either over RPC or by creating records that can be posted to the
 * message bus to be handled eventually.
 */
@Suppress("TooManyFunctions")
interface MembershipPersistenceClient : Lifecycle {
    /**
     * Persists a list of member info records as viewed by a specific holding identity.
     * Member Infos which already exist are updated.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param memberInfos The list of member information to persist.
     *
     * @return membership persistence operation.
     */
    fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceOperation<Unit>

    /**
     * Persist a new version of the group policy.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param groupPolicy The group policy.
     *
     * @return membership persistence operation.
     *  In the case of success the payload will include the newly created version number.
     */
    fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
    ): MembershipPersistenceOperation<Int>

    /**
     * Create and persist the first version of group parameters. This method is expected to be used by an MGM to persist
     * the initial snapshot that contains basic fields defined in [GroupParameters]. The group parameters persisted in
     * this method do not contain other properties such as notary service information.
     *
     * This operation is idempotent.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     *
     * @return Membership persistence operation. In the case of success, the payload
     * will include a [KeyValuePairList] of the newly persisted group parameters.
     */
    fun persistGroupParametersInitialSnapshot(
        viewOwningIdentity: HoldingIdentity
    ): MembershipPersistenceOperation<KeyValuePairList>

    /**
     * Persists a set of group parameters. This method is expected to be used by members to persist group parameters
     * as distributed by the MGM.
     *
     * The epoch of the new [groupParameters] to be persisted must be higher than that of previous group parameter versions.
     *
     * This operation is idempotent.
     *
     * @param viewOwningIdentity The holding identity owning this view of the group parameters.
     * @param groupParameters The group parameters to persist.
     *
     * @return Operation with the latest group parameters for that holding identity.
     */
    fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: GroupParameters
    ): MembershipPersistenceOperation<GroupParameters>

    /**
     * Adds notary information to an existing set of group parameters. This method is expected to be used by an MGM to
     * either add a notary vnode to a new notary service, or add a new notary vnode (or notary vnode with rotated keys)
     * to an existing notary service within the group parameters. If successful, a new set of group parameters containing
     * the specified notary information is persisted.
     *
     * If adding a notary vnode to an existing notary service, the optional plugin name, if specified, must match
     * that of the notary service.
     *
     * This operation is idempotent.
     *
     * @param viewOwningIdentity The holding identity owning this view of the group parameters.
     * @param notary [MemberInfo] of the notary to be added.
     *
     * @return Membership persistence operation. In the case of success, the payload
     * will include a [KeyValuePairList] of the newly persisted group parameters.
     */
    fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo
    ): MembershipPersistenceOperation<KeyValuePairList>

    /**
     * Persists a registration request record as viewed by a specific holding identity.
     * The registration request is updated if it already exists.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationRequest The registration request to persist.
     *
     * @return membership persistence operation.
     *  No payload is returned in the case of success.
     */
    fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceOperation<Unit>

    /**
     * Set a member and registration request as approved
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param approvedMember The member that had been approved
     * @param registrationRequestId The ID of the registration request
     *
     * @return membership persistence operation.
     */
    fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceOperation<MemberInfo>

    /**
     * Set the status of an existing registration request.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationId The ID of the registration request.
     * @param registrationRequestStatus The new status of the registration request.
     * @param reason Reason why the status specified by [registrationRequestStatus] is being set.
     *
     * @return membership persistence operation.
     *  No payload is returned in the case of success.
     */
    fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String? = null,
    ): MembershipPersistenceOperation<Unit>

    /**
     * Add mutual TLS client certificate subject to the allowed list.
     *
     * @param mgmHoldingIdentity The holding identity of the MGM.
     * @param subject The client certificate subject.
     *
     * @return membership persistence operation.
     *  No payload is returned in the case of success.
     */
    fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceOperation<Unit>

    /**
     * Remove mutual TLS client certificate subject from the allowed list.
     *
     * @param mgmHoldingIdentity The holding identity of the MGM.
     * @param subject The client certificate subject.
     *
     * @return membership persistence operation.
     *  No payload is returned in the case of success.
     */
    fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceOperation<Unit>

    /**
     * Adds a new pre auth token to the database.
     *
     * @param mgmHoldingIdentity The holding identity of the mgm.
     * @param preAuthTokenId A unique token identifier of the pre auth token.
     * @param ownerX500Name The X500 name of the owner of the pre auth token.
     * @param ttl A timestamp for when the pre auth token expires. If null the token never expires.
     * @param remarks An optional remark added when the token was created.
     *
     * @return membership persistence operation.
     *  No payload is returned in the case of success.
     */
    fun generatePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?
    ): MembershipPersistenceOperation<Unit>

    /**
     * Consumes a pre-auth token provided it exists for the member. If the token was successfully consumed, this method
     * returns [MembershipPersistenceResult.Success]. Otherwise, it will return [MembershipPersistenceResult.Failure].
     *
     * @param mgmHoldingIdentity The holding identity of the mgm.
     * @param preAuthTokenId A unique token identifier of the pre-auth token.
     * @param ownerX500Name The X500 name of the owner of the pre-auth token.
     *
     * @return membership persistence operation.
     */
    fun consumePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name,
        preAuthTokenId: UUID
    ): MembershipPersistenceOperation<Unit>

    /**
     * Revoke an existing pre auth token in the database.
     *
     * @param mgmHoldingIdentity The holding identity of the mgm.
     * @param preAuthTokenId The unique token identifier of the pre auth token being revoked.
     * @param remarks An optional remark about why the token was revoked.
     *
     * @return membership persistence operation with the updated token.
     */
    fun revokePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        remarks: String?
    ): MembershipPersistenceOperation<PreAuthToken>

    /**
     * Persists the specified approval rule.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param ruleParams Parameters of the rule to be added, represented by [ApprovalRuleParams].
     *
     * @return Membership persistence operation with the details of the newly added rule.
     */
    fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleParams: ApprovalRuleParams
    ): MembershipPersistenceOperation<ApprovalRuleDetails>

    /**
     * Deletes a previously persisted approval rule.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param ruleId ID of the group approval rule to be deleted.
     *
     * @return Membership persistence operation.
     *  No payload is returned in the case of success.
     */
    fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleId: String,
        ruleType: ApprovalRuleType
    ): MembershipPersistenceOperation<Unit>
}
