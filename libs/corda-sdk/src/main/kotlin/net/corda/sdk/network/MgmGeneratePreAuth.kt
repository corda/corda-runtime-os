package net.corda.sdk.network

import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.types.request.PreAuthTokenRequest
import net.corda.membership.rest.v1.types.response.PreAuthToken
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MgmGeneratePreAuth {

    /**
     * Generate a PRE_AUTH token
     * @param restClient of type RestClient<MGMRestResource>
     * @param holdingIdentityShortHash the holding identity ID of the MGM
     * @param request of type [PreAuthTokenRequest] used as request payload
     * @param wait Duration before timing out, default 10 seconds
     * @return token details
     */
    fun generatePreAuthToken(
        restClient: RestClient<MGMRestResource>,
        holdingIdentityShortHash: String,
        request: PreAuthTokenRequest,
        wait: Duration = 10.seconds
    ): PreAuthToken {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Generate pre-auth token"
            ) {
                val resource = client.start().proxy
                resource.generatePreAuthToken(holdingIdentityShortHash, request)
            }
        }
    }
}
