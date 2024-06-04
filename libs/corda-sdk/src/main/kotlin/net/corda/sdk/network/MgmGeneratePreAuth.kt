package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.types.request.PreAuthTokenRequest
import net.corda.membership.rest.v1.types.response.PreAuthToken
import net.corda.restclient.CordaRestClient

class MgmGeneratePreAuth(val restClient: CordaRestClient) {

    /**
     * Generate a PRE_AUTH token
     * @param holdingIdentityShortHash the holding identity ID of the MGM
     * @param request of type [PreAuthTokenRequest] used as request payload
     * @return token details
     */
    fun generatePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        request: PreAuthTokenRequest
    ): PreAuthToken {
        // TODO add waiting mechanism??
        return restClient.mgmClient.postMgmHoldingidentityshorthashPreauthtoken(holdingIdentityShortHash.value, request)
    }
}
