package net.corda.membership.persistence.client

import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

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
    fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String
    ): MembershipQueryResult<RegistrationRequest>

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
}

