package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant

class RegistrationsLookup {

    fun checkRegistrations(
        restClient: RestClient<MemberRegistrationRestResource>,
        holdingIdentityShortHash: String
    ): List<RestRegistrationRequestStatus> {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to lookup registrations after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.checkRegistrationProgress(holdingIdentityShortHash)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun checkRegistration(
        restClient: RestClient<MemberRegistrationRestResource>,
        holdingIdentityShortHash: String,
        requestId: String
    ): RestRegistrationRequestStatus {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to lookup registration after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.checkSpecificRegistrationProgress(holdingIdentityShortHash, requestId)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
