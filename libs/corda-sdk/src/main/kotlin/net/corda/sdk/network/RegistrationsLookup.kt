package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RegistrationStatus
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationsLookup {

    /**
     * List all the registrations a given node knows about
     * @param restClient of type RestClient<MemberRegistrationRestResource>
     * @param holdingIdentityShortHash the holding identity ID of the node
     * @param wait Duration before timing out, default 10 seconds
     * @return list of Registration requests
     */
    fun checkRegistrations(
        restClient: RestClient<MemberRegistrationRestResource>,
        holdingIdentityShortHash: String,
        wait: Duration = 10.seconds
    ): List<RestRegistrationRequestStatus> {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Lookup registrations"
            ) {
                val resource = client.start().proxy
                resource.checkRegistrationProgress(holdingIdentityShortHash)
            }
        }
    }

    /**
     * List the details of a specific registration
     * @param restClient of type RestClient<MemberRegistrationRestResource>
     * @param holdingIdentityShortHash the holding identity ID of the node
     * @param requestId the ID returned from the registration submission
     * @param wait Duration before timing out, default 10 seconds
     * @return details of the specified registration request
     */
    fun checkRegistration(
        restClient: RestClient<MemberRegistrationRestResource>,
        holdingIdentityShortHash: String,
        requestId: String,
        wait: Duration = 10.seconds
    ): RestRegistrationRequestStatus {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Lookup registration $requestId"
            ) {
                val resource = client.start().proxy
                resource.checkSpecificRegistrationProgress(holdingIdentityShortHash, requestId)
            }
        }
    }

    /**
     * Wait for the status of the given registration to be APPROVED or throw exception
     * @param restClient of type RestClient<MemberRegistrationRestResource>
     * @param registrationId the ID returned from the registration submission
     * @param holdingId the holding identity ID of the node
     * @param wait Duration before timing out, default 60 seconds
     */
    fun waitForRegistrationApproval(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationId: String,
        holdingId: String,
        wait: Duration = 60.seconds
    ) {
        restClient.use {
            executeWithRetry(
                waitDuration = wait,
                operationName = "Check registration progress"
            ) {
                val status = checkRegistration(restClient, holdingId, registrationId, wait)
                when (val registrationStatus = status.registrationStatus) {
                    RegistrationStatus.APPROVED -> true // Return true to indicate the condition is satisfied
                    RegistrationStatus.DECLINED,
                    RegistrationStatus.INVALID,
                    RegistrationStatus.FAILED,
                    -> throw OnboardException("Status of registration is $registrationStatus; reason: ${status.reason}.")
                    else -> throw OnboardException("Status of registration is $registrationStatus; reason: ${status.reason}.")
                }
            }
        }
    }

    /**
     * Combination method to submit the registration and wait for it to be approved
     * @param restClient of type RestClient<MemberRegistrationRestResource>
     * @param registrationContext the payload to be used in the request, see [RegistrationContext]
     * @param holdingId the holding identity ID of the node
     * @param wait Duration before timing out, default 60 seconds
     * @return ID of the registration request
     */
    fun registerAndWaitForApproval(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationContext: Map<String, Any>,
        holdingId: String,
        wait: Duration = 60.seconds
    ) {
        val response = RegistrationRequester().requestRegistration(restClient, registrationContext, holdingId, wait)
        waitForRegistrationApproval(restClient, response.registrationId, holdingId, wait)
    }
}
