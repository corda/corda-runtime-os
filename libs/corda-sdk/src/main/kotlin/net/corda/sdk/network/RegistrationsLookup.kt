package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.MemberRegistrationRequest
import net.corda.restclient.generated.models.RestRegistrationRequestStatus
import net.corda.restclient.generated.models.RestRegistrationRequestStatus.RegistrationStatus
import net.corda.sdk.data.RequestId
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationsLookup(val restClient: CordaRestClient) {

    /**
     * List all the registrations a given node knows about
     * @param holdingIdentityShortHash the holding identity ID of the node
     * @param wait Duration before timing out, default 10 seconds
     * @return list of Registration requests
     */
    fun checkRegistrations(
        holdingIdentityShortHash: ShortHash,
        wait: Duration = 10.seconds
    ): List<RestRegistrationRequestStatus> {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Lookup registrations"
        ) {
            restClient.memberRegistrationClient.getMembershipHoldingidentityshorthash(holdingIdentityShortHash.value)
        }
    }

    /**
     * Call the [checkRegistrations] function, and check if registration status is approved
     */
    fun isVnodeRegistrationApproved(
        holdingIdentityShortHash: ShortHash,
        wait: Duration = 10.seconds
    ): Boolean {
        val registrationStatuses = checkRegistrations(holdingIdentityShortHash, wait)
        return registrationStatuses.any { request ->
            request.registrationStatus == RegistrationStatus.APPROVED
        }
    }

    /**
     * List the details of a specific registration
     * @param holdingIdentityShortHash the holding identity ID of the node
     * @param requestId the ID returned from the registration submission
     * @param wait Duration before timing out, default 10 seconds
     * @return details of the specified registration request
     */
    fun checkRegistration(
        holdingIdentityShortHash: ShortHash,
        requestId: RequestId,
        wait: Duration = 10.seconds
    ): RestRegistrationRequestStatus {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Lookup registration $requestId"
        ) {
            restClient.memberRegistrationClient
                .getMembershipHoldingidentityshorthashRegistrationrequestid(
                    holdingIdentityShortHash.value,
                    requestId.value
                )
        }
    }

    /**
     * Wait for the status of the given registration to be APPROVED or throw exception
     * @param registrationId the ID returned from the registration submission
     * @param holdingId the holding identity ID of the node
     * @param wait Duration before timing out, default 60 seconds
     */
    fun waitForRegistrationApproval(
        registrationId: RequestId,
        holdingId: ShortHash,
        wait: Duration = 60.seconds
    ) {
        executeWithRetry(
            waitDuration = wait,
            operationName = "Check registration progress"
        ) {
            val status = checkRegistration(holdingId, registrationId, wait)
            when (val registrationStatus = status.registrationStatus) {
                RegistrationStatus.APPROVED -> true // Return true to indicate the condition is satisfied
                RegistrationStatus.DECLINED,
                RegistrationStatus.INVALID,
                RegistrationStatus.FAILED,
                -> throw OnboardFailedException("Onboarding failed with status: $registrationStatus; reason: ${status.reason}.")
                else -> throw OnboardException("Status of registration is $registrationStatus; reason: ${status.reason}.")
            }
        }
    }

    /**
     * Combination method to submit the registration and wait for it to be approved
     * @param memberRegistrationRequest the payload to be used in the request, see [RegistrationRequest]
     * @param holdingId the holding identity ID of the node
     * @param wait Duration before timing out, default 60 seconds
     * @return ID of the registration request
     */
    fun registerAndWaitForApproval(
        memberRegistrationRequest: MemberRegistrationRequest,
        holdingId: ShortHash,
        wait: Duration = 60.seconds
    ) {
        val response = RegistrationRequester(restClient).requestRegistration(memberRegistrationRequest, holdingId)
        waitForRegistrationApproval(RequestId(response.registrationId), holdingId, wait)
    }
}

class OnboardException(message: String) : Exception(message)

class OnboardFailedException(message: String) : Exception(message)
