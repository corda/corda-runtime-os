package net.corda.sdk.network

import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant

class ExportGroupPolicyFromMgm {

    fun exportPolicy(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String): String {
        return restClient.use { client ->
            checkInvariant(
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
