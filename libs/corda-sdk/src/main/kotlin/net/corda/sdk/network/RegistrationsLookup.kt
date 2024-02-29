package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationsLookup {

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
}
