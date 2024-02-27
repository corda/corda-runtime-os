package net.corda.sdk.network

import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.types.request.PreAuthTokenRequest
import net.corda.membership.rest.v1.types.response.PreAuthToken
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant

class MgmGeneratePreAuth {

    fun generatePreAuthToken(
        restClient: RestClient<MGMRestResource>,
        holdingIdentityShortHash: String,
        request: PreAuthTokenRequest
    ): PreAuthToken {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to generate pre-auth token for $holdingIdentityShortHash after ${MAX_ATTEMPTS} attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.generatePreAuthToken(holdingIdentityShortHash, request)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
