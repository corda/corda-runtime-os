package net.corda.membership.httprpc.v1

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestStatus

/**
 * The Member Registration API consists of a number of endpoints which manage holding identities' participation in
 * membership groups. To participate in a membership group, the holding identity is required to make a registration
 * request that needs to be approved by the MGM for that group. This API allows you to start the registration process
 * for a holding identity, and check the status of a previously created registration request.
 */
@HttpRestResource(
    name = "Member Registration API",
    description = "The Member Registration API consists of a number of endpoints which manage holding identities'" +
            " participation in membership groups. To participate in a membership group, the holding identity is" +
            " required to make a registration request that needs to be approved by the MGM for that group." +
            " This API allows you to start the registration process for a holding identity, and check the status of" +
            " a previously created registration request.",
    path = "membership"
)
interface MemberRegistrationRestResource : RestResource {
    /**
     * The [startRegistration] method enables you to start the registration process for a holding identity represented
     * by [holdingIdentityShortHash]. Re-registration is not currently supported, if a holding identity has previously
     * registered successfully, this endpoint may not be used again.
     *
     * Example usage:
     * ```
     * memberRegistrationOps.startRegistration(holdingIdentityShortHash = "58B6030FABDD", memberRegistrationRequest
     * = MemberRegistrationRequest(action = "requestJoin", context = {"corda.session.key.id": "D2FAF709052F"}))
     * ```
     *
     * @param holdingIdentityShortHash The holding identity ID of the requesting virtual node.
     * @param memberRegistrationRequest The request sent during registration which contains the requested registration
     * action (e.g. 'requestJoin') along with a context map containing data required to initiate the registration process.
     *
     * @return [RegistrationRequestProgress] to indicate the status of the request at time of submission.
     */
    @HttpPOST(
        path = "{holdingIdentityShortHash}",
        description = "This method starts the registration process for a holding identity.",
        responseDescription = """
            The registration progress information, including:
            registrationId: the registration request ID
            registrationSent: the date and the when the registration progress started; 
                value of null indicated that registration has not started yet
            registrationStatus: the status of the registration request; 
                possible values are "SUBMITTED and "NOT_SUBMITTED"
            memberInfoSubmitted: the properties submitted to MGM during the registration     
        """
    )
    fun startRegistration(
        @RestPathParameter(description = "The holding identity ID of the requesting virtual node")
        holdingIdentityShortHash: String,
        @RestRequestBodyParameter(
            description = "The request sent during registration which contains the requested registration action" +
                    " (e.g. 'requestJoin') along with a context map containing data required to initiate the registration process."
        )
        memberRegistrationRequest: MemberRegistrationRequest
    ): RegistrationRequestProgress

    /**
     * The [checkRegistrationProgress] method enables you to check the statuses of all registration requests for a holding
     * identity. This method returns a list of statuses based on the holding identity's own local data, no outward
     * communication is involved.
     *
     * Example usage:
     * ```
     * memberRegistrationOps.checkRegistrationProgress(holdingIdentityShortHash = "58B6030FABDD")
     * ```
     *
     * @param holdingIdentityShortHash The ID of the holding identity whose view of the registration progress is to be checked.
     *
     * @return List of [RegistrationRequestStatus] to indicate the last known statuses of all registration requests made
     * by [holdingIdentityShortHash].
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}",
        description = "This method checks the statuses of all registration requests for a specified holding identity.",
        responseDescription = """
            The registration status information, including:
            registrationId: the registration request ID
            registrationSent: the date and the when the registration progress started; 
                value of null indicated that registration has not started yet
            registrationUpdated: the date and the when the registration has been last updated    
            registrationStatus: the status of the registration request; 
                possible values are "NEW", "PENDING_MEMBER_VERIFICATION", "PENDING_APPROVAL_FLOW", 
                "PENDING_MANUAL_APPROVAL", "PENDING_AUTO_APPROVAL", "DECLINED", or "APPROVED"
            memberInfoSubmitted: the properties submitted to MGM during the registration     
        """
    )
    fun checkRegistrationProgress(
        @RestPathParameter(description = "The ID of the holding identity whose view of the registration progress is to be checked.")
        holdingIdentityShortHash: String
    ): List<RegistrationRequestStatus>

    /**
     * The [checkSpecificRegistrationProgress] method enables you to check the status of the registration request
     * specified by [registrationRequestId] for a holding identity. This method returns the status based on the holding
     * identity's own local data, no outward communication is involved.
     *
     * Example usage:
     * ```
     * memberRegistrationOps.checkSpecificRegistrationProgress(holdingIdentityShortHash = "58B6030FABDD",
     * registrationRequestId = "3B9A266F96E2")
     * ```
     *
     * @param holdingIdentityShortHash The ID of the holding identity whose view of the registration progress is to be checked.
     * @param registrationRequestId The ID of the registration request.
     *
     * @return [RegistrationRequestStatus] to indicate the last known status of the specified registration request made
     * by [holdingIdentityShortHash].
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}/{registrationRequestId}",
        description = "This method checks the status of the specified registration request for a holding identity.",
        responseDescription = """
            The registration status information, including:
            registrationId: the registration request ID
            registrationSent: the date and the when the registration progress started; 
                value of null indicated that registration has not started yet
            registrationUpdated: the date and the when the registration has been last updated    
            registrationStatus: the status of the registration request; 
                possible values are "NEW", "PENDING_MEMBER_VERIFICATION", "PENDING_APPROVAL_FLOW", 
                "PENDING_MANUAL_APPROVAL", "PENDING_AUTO_APPROVAL", "DECLINED", or "APPROVED"
            memberInfoSubmitted: the properties submitted to MGM during the registration     
        """
    )
    fun checkSpecificRegistrationProgress(
        @RestPathParameter(description = "The ID of the holding identity whose view of the registration progress is to be checked.")
        holdingIdentityShortHash: String,
        @RestPathParameter(description = "The ID of the registration request")
        registrationRequestId: String,
    ): RegistrationRequestStatus
}
