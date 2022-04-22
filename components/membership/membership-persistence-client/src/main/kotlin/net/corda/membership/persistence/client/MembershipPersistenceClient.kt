package net.corda.membership.persistence.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

/**
 * Interface to be implemented by the service which requires membership persistence outside of the DB worker.
 * This client handles persistence over RPC.
 */
interface MembershipPersistenceClient : Lifecycle {
    /**
     * Persists a list of member info records as viewed by a specific holding identity.
     * Member Infos which already exist are updated.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param memberInfos The list of member information to persist.
     *
     * @return membership persistence result to indicate the result of the persistence operation.
     */
    fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceResult

    /**
     * Persists a single member info record as viewed by a specific holding identity.
     * Member Info is updated if it already exists.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param memberInfo The member info to persist.
     *
     * @return membership persistence result to indicate the result of the persistence operation.
     */
    fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfo: MemberInfo
    ): MembershipPersistenceResult

    /**
     * Persists a registration request record as viewed by a specific holding identity.
     * The registration request is updated if it already exists.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationRequest The registration request to persist.
     *
     * @return membership persistence result to indicate the result of the persistence operation.
     */
    fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceResult
}

