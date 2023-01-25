package net.corda.membership.persistence.client

import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.util.UUID

interface MembershipQueryClient : Lifecycle {
    /**
     * Query for all members visible by a specific holding identity.
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     *
     * @return a query result with a collection of member infos if the query executed successfully.
     */
    fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity
    ): MembershipQueryResult<Collection<MemberInfo>>

    /**
     * Query for all members matching the given holding identities as visible by a specific holding identity.
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     * @param queryFilter A collection of holding identities to query for.
     *
     * @return a query result with a collection of member infos if the query executed successfully.
     */
    fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        queryFilter: Collection<HoldingIdentity>
    ): MembershipQueryResult<Collection<MemberInfo>>

    /**
     * Query for a registration request for a specific holding identity based on the registration request ID.
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     * @param registrationId the registration ID to query for.
     *
     * @return a query result with a matching registration request if the query executed successfully.
     */
    fun queryRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String
    ): MembershipQueryResult<RegistrationRequestStatus?>

    /**
     * Query for all the registration requests for a specific holding identity.
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     *
     * @return a query result with a matching registration request if the query executed successfully.
     */
    fun queryRegistrationRequestsStatus(
        viewOwningIdentity: HoldingIdentity
    ): MembershipQueryResult<List<RegistrationRequestStatus>>

    /**
     * MGM query for registration requests submitted by members for joining the membership group.
     *
     * @param mgmHoldingIdentity The holding identity of the MGM.
     * @param requestingMemberX500Name Optional. X.500 name of the requesting member.
     * @param viewHistoric Optional. Set this to 'true' to view both in-progress and completed (historic) requests.
     * Defaults to 'false' (in-progress requests only).
     *
     * @return A query result with the collection of request statuses [RegistrationRequestStatus] if the query
     * executed successfully. Returns an empty [Collection] if no requests have been submitted.
     */
    fun queryRegistrationRequests(
        mgmHoldingIdentity: HoldingIdentity,
        requestingMemberX500Name: String?,
        viewHistoric: Boolean
    ): MembershipQueryResult<Collection<RegistrationRequestStatus>>

    /**
     * Query for members signatures.
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     * @param holdingsIdentities the members holding identities.
     *
     * @return a query result with a matching registration request if the query executed successfully.
     */
    fun queryMembersSignatures(
        viewOwningIdentity: HoldingIdentity,
        holdingsIdentities: Collection<HoldingIdentity>,
    ): MembershipQueryResult<Map<HoldingIdentity, CryptoSignatureWithKey>>

    /**
     * Query for GroupPolicy.
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     *
     * @return a query result with the latest version of group policy if the query executed successfully.
     * Returns an empty [LayeredPropertyMap] if there was no group policy persisted.
     */
    fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<LayeredPropertyMap>


    /**
     * Retrieve the list of the mutual TLS client certificate subject in the allowed list.
     *
     * @param mgmHoldingIdentity The holding identity of the MGM.
     *
     * @return a query result with the list of the client certificates subject.
     */
    fun mutualTlsListAllowedCertificates(mgmHoldingIdentity: HoldingIdentity): MembershipQueryResult<Collection<String>>

    /**
     * Query for PreAuthTokens
     *
     * @param mgmHoldingIdentity The holding identity of the MGM.
     * @param ownerX500Name The X500 name of the member to query for.
     * @param preAuthTokenId The token ID to query for.
     * @param viewInactive Return tokens with status [PreAuthTokenStatus.REVOKED], [PreAuthTokenStatus.CONSUMED],
     * [PreAuthTokenStatus.AUTO_INVALIDATED] as well as [PreAuthTokenStatus.AVAILABLE].
     */
    fun queryPreAuthTokens(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean
    ): MembershipQueryResult<List<PreAuthToken>>

    /**
     * Retrieves all persisted rules of the specified [ruleType].
     *
     * @param viewOwningIdentity The holding identity whose view is being requested.
     * @param ruleType The approval rule type for this rule. See [ApprovalRuleType] for the available types.
     *
     * @return A query result with the collection of regular expressions if the query executed successfully.
     * Returns an empty [List] if no rules have been persisted.
     */
    fun getApprovalRules(
        viewOwningIdentity: HoldingIdentity,
        ruleType: ApprovalRuleType
    ): MembershipQueryResult<Collection<ApprovalRuleDetails>>
}

