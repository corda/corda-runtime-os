package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.RestGroupParameters
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils

class GroupParametersLookup {

    fun lookupGroupParameters(restClient: RestClient<MemberLookupRestResource>, holdingIdentityShortHash: String): RestGroupParameters {
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Failed to lookup group parameters after ${InvariantUtils.MAX_ATTEMPTS} attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.viewGroupParameters(holdingIdentityShortHash)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
