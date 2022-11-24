package net.corda.membership.persistence.client

import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatus
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.GroupParameters
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
     * Create and persist the first version of group parameters. This method is expected to be used by an MGM to persist
     * the initial snapshot that contains basic fields defined in [GroupParameters]. The group parameters persisted in
     * this method do not contain other properties such as notary service information.
     *
     * @param viewOwningIdentity The holding identity of the owner of the view of data.
     *
     * @return Membership persistence result to indicate the result of the operation. In the case of success, the payload
     * will include a [KeyValuePairList] of the newly persisted group parameters.
     */
    fun persistGroupParametersInitialSnapshot(
        viewOwningIdentity: HoldingIdentity
    ): MembershipPersistenceResult<KeyValuePairList>

    /**
     * Persists a set of group parameters. This method is expected to be used by members to persist group parameters
     * as distributed by the MGM.
     *
     * The epoch of the new [groupParameters] to be persisted must be higher than that of previous group parameter versions.
     *
     * @param viewOwningIdentity The holding identity owning this view of the group parameters.
     * @param groupParameters The group parameters to persist.
     *
     * @return Membership persistence result to indicate the result of the operation. In the case of success, the payload
     * will include a [KeyValuePairList] of the newly persisted group parameters.
     */
    fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: GroupParameters
    ): MembershipPersistenceResult<KeyValuePairList>

    /**
     * Adds notary information to an existing set of group parameters. This method is expected to be used by an MGM to
     * either add a notary vnode to a new notary service, or add a new notary vnode (or notary vnode with rotated keys)
     * to an existing notary service within the group parameters. If successful, a new set of group parameters containing
     * the specified notary information is persisted.
     *
     * If adding a notary vnode to an existing notary service, the optional plugin name, if specified, must match
     * that of the notary service.
     *
     * @param viewOwningIdentity The holding identity owning this view of the group parameters.
     * @param notary [MemberInfo] of the notary to be added.
     *
     * @return Membership persistence result to indicate the result of the operation. In the case of success, the payload
     * will include a [KeyValuePairList] of the newly persisted group parameters.
     */
    fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo
    ): MembershipPersistenceResult<KeyValuePairList>

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
