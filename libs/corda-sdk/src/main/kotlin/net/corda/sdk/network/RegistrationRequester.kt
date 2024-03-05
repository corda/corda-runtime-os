package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.NetworkRestResource
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationRequester {

    /**
     * Submit the registration
     * @param restClient of type RestClient<MemberRegistrationRestResource>
     * @param registrationContext the payload to be used in the request, see [RegistrationContext]
     * @param holdingId the holding identity of the node
     * @param wait Duration before timing out, default 10 seconds
     * @return ID of the registration request
     */
    fun requestRegistration(
        restClient: RestClient<MemberRegistrationRestResource>,
        registrationContext: Map<String, String>,
        holdingId: String,
        wait: Duration = 10.seconds
    ): RegistrationRequestProgress {
        val request = MemberRegistrationRequest(
            context = registrationContext,
        )
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Request registration"
            ) {
                val resource = client.start().proxy
                resource.startRegistration(holdingId, request)
            }
        }
    }

    /**
     * Configure a given node as a network participant
     * @param restClient of type RestClient<NetworkRestResource>
     * @param request of type HostedIdentitySetupRequest
     * @param holdingId the holding identity of the node
     * @param wait Duration before timing out, default 10 seconds
     */
    fun configureAsNetworkParticipant(
        restClient: RestClient<NetworkRestResource>,
        request: HostedIdentitySetupRequest,
        holdingId: String,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Configure $holdingId as network participant"
            ) {
                val resource = client.start().proxy
                resource.setupHostedIdentities(holdingId, request)
            }
        }
    }
}
