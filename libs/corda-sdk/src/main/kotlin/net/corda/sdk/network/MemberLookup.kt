package net.corda.sdk.network

import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfoList
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant

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
        status: List<String>
    ): RestMemberInfoList {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to lookup member after $MAX_ATTEMPTS attempts.",
            ) {
                try {
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
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
