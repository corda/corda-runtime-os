package net.corda.sdk.network

import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ExportGroupPolicyFromMgm {

    fun exportPolicy(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String, wait: Duration = 10.seconds): String {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Export group policy"
            ) {
                val resource = client.start().proxy
                resource.generateGroupPolicy(holdingIdentityShortHash)
            }
        }
    }
}
