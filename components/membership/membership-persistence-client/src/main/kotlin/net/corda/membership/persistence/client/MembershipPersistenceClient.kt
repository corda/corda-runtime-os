package net.corda.membership.persistence.client

import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.v5.base.types.LayeredPropertyMap
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
     *  No payload is returned in the case of success.
     */
    fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceResult<Unit>

    /**
     * Persist a new version of the group policy.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param groupPolicy The group policy.
     *
     * @return membership persistence result to indicate the result of the persistence operation.
     *  In the case of success the payload will include the newly created version number.
     */
    fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
    ): MembershipPersistenceResult<Int>

    /**
     * Persists a registration request record as viewed by a specific holding identity.
     * The registration request is updated if it already exists.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationRequest The registration request to persist.
     *
     * @return membership persistence result to indicate the result of the persistence operation.
     *  No payload is returned in the case of success.
     */
    fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceResult<Unit>

    /**
     * Set a member and registration request as approved
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param approvedMember The member that had been approved
     * @param registrationRequestId The ID of the registration request
     *
     * @return membership persistence result with the persisted member information
     *  indicate the result of the persistence operation.
     */
    fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceResult<MemberInfo>

    /**
     * Set a member and registration request as declined
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param declinedMember The member that had been declined
     * @param registrationRequestId The ID of the registration request
     *
     * @return membership persistence result with the persisted member information to indicate the result of the
     * persistence operation. No payload is returned in case of success.
     */
    fun setMemberAndRegistrationRequestAsDeclined(
        viewOwningIdentity: HoldingIdentity,
        declinedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceResult<Unit>

    /**
     * Set the status of an existing registration request.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationId The ID of the registration request.
     * @param registrationRequestStatus The new status of the registration request.
     *
     * @return membership persistence result to indicate the result of the persistence operation.
     *  No payload is returned in the case of success.
     */
    fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus
    ): MembershipPersistenceResult<Unit>
}
