package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.RestMemberInfoList
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MemberLookup(val restClient: CordaRestClient) {

    /**
     * Look up the Member details
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
        holdingIdentityShortHash: ShortHash,
        commonName: String?,
        organization: String?,
        organizationUnit: String?,
        locality: String?,
        state: String?,
        country: String?,
        status: List<String> = listOf("ACTIVE"),
        wait: Duration = 10.seconds
    ): RestMemberInfoList {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Lookup member"
        ) {
            restClient.memberLookupClient.getMembersHoldingidentityshorthash(
                holdingidentityshorthash = holdingIdentityShortHash.value,
                cn = commonName,
                o = organization,
                ou = organizationUnit,
                l = locality,
                st = state,
                c = country,
                statuses = status
            )
        }
    }
}
