package net.corda.sdk.network

import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant

class ClientCertificates {

    fun allowMutualTlsForSubjects(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String, subjects: Collection<String>) {
        restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to allow mutual TLS certificates after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    subjects.forEach { subject ->
                        resource.mutualTlsAllowClientCertificate(holdingIdentityShortHash, subject)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun listMutualTlsClientCertificates(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String): Collection<String> {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to list mutual TLS certificates after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.mutualTlsListClientCertificate(holdingIdentityShortHash)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
