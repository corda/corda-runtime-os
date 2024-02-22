package net.corda.sdk.network

import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.client.RestClient
import net.corda.sdk.network.InvariantUtils.checkInvariant

class ExportGroupPolicyFromMgm {

    companion object {
        const val MAX_ATTEMPTS = 10
        const val WAIT_INTERVAL = 2000L
    }

    fun exportPolicy(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String): String {
        return restClient.use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Failed to export group policy after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.generateGroupPolicy(holdingIdentityShortHash)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
