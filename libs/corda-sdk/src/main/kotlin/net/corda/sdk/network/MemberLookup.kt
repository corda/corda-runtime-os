package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfoList
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MemberLookup {

    @Suppress("LongParameterList")
    fun lookupMember(
        restClient: RestClient<MemberLookupRestResource>,
        holdingIdentityShortHash: String,
        commonName: String?,
        organization: String?,
        organizationUnit: String?,
        locality: String?,
        state: String?,
        country: String?,
        status: List<String>,
        wait: Duration = 10.seconds
    ): RestMemberInfoList {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Lookup member"
            ) {
                val resource = client.start().proxy
                resource.lookupV51(
                    holdingIdentityShortHash,
                    commonName,
                    organization,
                    organizationUnit,
                    locality,
                    state,
                    country,
                    status.orEmpty(),
                )
            }
        }
    }
}
