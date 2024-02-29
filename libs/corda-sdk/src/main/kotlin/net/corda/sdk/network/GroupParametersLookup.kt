package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.RestGroupParameters
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GroupParametersLookup {

    fun lookupGroupParameters(
        restClient: RestClient<MemberLookupRestResource>,
        holdingIdentityShortHash: String,
        wait: Duration = 10.seconds
    ): RestGroupParameters {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Lookup group parameters"
            ) {
                val resource = client.start().proxy
                resource.viewGroupParameters(holdingIdentityShortHash)
            }
        }
    }
}
