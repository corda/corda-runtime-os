package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfoList
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MemberLookup {

    /**
     * Look up the Member details
     * @param restClient of type RestClient<MemberLookupRestResource>
     * @param holdingIdentityShortHash the holding identity of the node
     * @param commonName optional search criteria
     * @param organization optional search criteria
     * @param organizationUnit optional search criteria
     * @param locality optional search criteria
     * @param state optional search criteria
     * @param country optional search criteria
     * @param status list of statuses, can include ACTIVE and SUSPENDED. Only ACTIVE by default
     * @param wait Duration before timing out, default 10 seconds
     * @return list of Member info
     */
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
        status: List<String> = listOf("ACTIVE"),
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
                    status,
                )
            }
        }
    }
}
