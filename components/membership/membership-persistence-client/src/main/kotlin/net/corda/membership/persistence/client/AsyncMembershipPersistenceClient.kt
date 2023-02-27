package net.corda.membership.persistence.client

import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

/**
 * A client that produces records to teh DB worker to be process asynchronously.
 */
interface AsyncMembershipPersistenceClient {
    /**
     * Persists a list of member info records as viewed by a specific holding identity.
     * Member Infos which already exist are updated.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param memberInfos The list of member information to persist.
     *
     * @return a collection of records to be sent in order for the DB worker to persist the request.
     */
    fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): Collection<Record<*, *>>

    /**
     * Persists a registration request record as viewed by a specific holding identity.
     * The registration request is updated if it already exists.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationRequest The registration request to persist.
     *
     * @return a collection of records to be sent in order for the DB worker to persist the request.
     */
    fun createPersistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): Collection<Record<*, *>>

    /**
     * Set a member and registration request as approved
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param approvedMember The member that had been approved
     * @param registrationRequestId The ID of the registration request
     *
     * @return a collection of records to be sent in order for the DB worker to persist the request.
     */
    fun setMemberAndRegistrationRequestAsApprovedRequest(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): Collection<Record<*, *>>

    /**
     * Set a member and registration request as declined
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param declinedMember The member that had been declined
     * @param registrationRequestId The ID of the registration request
     *
     * @return a collection of records to be sent in order for the DB worker to persist the request.
     */
    fun setMemberAndRegistrationRequestAsDeclinedRequest(
        viewOwningIdentity: HoldingIdentity,
        declinedMember: HoldingIdentity,
        registrationRequestId: String,
    ): Collection<Record<*, *>>

    /**
     * Set the status of an existing registration request.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     * @param registrationId The ID of the registration request.
     * @param registrationRequestStatus The new status of the registration request.
     *
     * @return a collection of records to be sent in order for the DB worker to persist the request.
     */
    fun setRegistrationRequestStatusRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus
    ): Collection<Record<*, *>>
}
