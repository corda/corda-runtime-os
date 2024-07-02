package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient

class ExportGroupPolicyFromMgm(
    val restClient: CordaRestClient
) {

    /**
     * Export the network policy from an MGM
     * @param holdingIdentityShortHash the holding identity of the MGM
     * @return policy as a String
     */
    fun exportPolicy(
        holdingIdentityShortHash: ShortHash
    ): String {
        return restClient.mgmClient.getMgmHoldingidentityshorthashInfo(holdingidentityshorthash = holdingIdentityShortHash.value)
    }
}
