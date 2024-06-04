package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.types.request.HostedIdentitySetupRequest
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.restclient.CordaRestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationRequester(val restClient: CordaRestClient) {

    /**
     * Submit the registration
     * @param memberRegistrationRequest [RegistrationRequest]
     * @param holdingId the holding identity of the node
     * @return ID of the registration request
     */
    fun requestRegistration(
        memberRegistrationRequest: MemberRegistrationRequest,
        holdingId: ShortHash,
    ): RegistrationRequestProgress {
        // TODO add wait/retry mechanism??
        return restClient.memberRegistrationClient.postMembershipHoldingidentityshorthash(holdingId.value, memberRegistrationRequest)
    }

    /**
     * Configure a given node as a network participant
     * @param request of type HostedIdentitySetupRequest
     * @param holdingId the holding identity of the node
     * @param wait Duration before timing out, default 10 seconds
     */
    fun configureAsNetworkParticipant(
        request: HostedIdentitySetupRequest,
        holdingId: ShortHash,
        wait: Duration = 10.seconds
    ) {
        executeWithRetry(
            waitDuration = wait,
            operationName = "Configure $holdingId as network participant"
        ) {
            restClient.networkClient.putNetworkSetupHoldingidentityshorthash(holdingId.value, request)
        }
    }
}
