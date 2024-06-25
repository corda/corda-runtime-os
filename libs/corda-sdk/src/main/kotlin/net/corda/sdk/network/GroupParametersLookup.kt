package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.RestGroupParameters
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GroupParametersLookup(val restClient: CordaRestClient) {

    /**
     * Look up the Group Parameters
     * @param holdingIdentityShortHash the holding identity of the node
     * @param wait Duration before timing out, default 10 seconds
     */
    fun lookupGroupParameters(
        holdingIdentityShortHash: ShortHash,
        wait: Duration = 10.seconds
    ): RestGroupParameters {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Lookup group parameters"
        ) {
            restClient.memberLookupClient.getMembersHoldingidentityshorthashGroupParameters(holdingIdentityShortHash.value)
        }
    }
}
