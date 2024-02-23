@file:Suppress("DEPRECATION")

package net.corda.sdk.network

import net.corda.membership.rest.v1.HsmRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.rest.client.RestClient
import net.corda.rest.client.exceptions.MissingRequestedResourceException
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME

class Keys {

    companion object {
        const val P2P_TLS_KEY_ALIAS = "p2p-tls-key"
        const val P2P_TLS_CERTIFICATE_ALIAS = "p2p-tls-cert"
    }

    fun assignSoftHsmAndGenerateKey(
        hsmRestClient: RestClient<HsmRestResource>,
        keysRestClient: RestClient<KeysRestResource>,
        holdingIdentityShortHash: String,
        category: String,
        scheme: String = ECDSA_SECP256R1_CODE_NAME
    ): String {
        hsmRestClient.use { hsmClient ->
            checkInvariant(
                errorMessage = "Assign Soft HSM operation for $category: failed after the maximum number of attempts ($MAX_ATTEMPTS).",
            ) {
                try {
                    hsmClient.start().proxy.assignSoftHsm(holdingIdentityShortHash, category)
                } catch (e: MissingRequestedResourceException) {
                    // This exception can be thrown while the assigning Hsm Key is being processed, so we catch it and re-try.
                    null
                }
            }
        }

        return generateKeyPair(
            keysRestClient = keysRestClient,
            tenantId = holdingIdentityShortHash,
            alias = "$holdingIdentityShortHash-$category",
            category = category,
            scheme = scheme
        )
    }

    fun generateKeyPair(
        keysRestClient: RestClient<KeysRestResource>,
        tenantId: String,
        alias: String,
        category: String,
        scheme: String = ECDSA_SECP256R1_CODE_NAME
    ): String {
        val response = keysRestClient.use { keyClient ->
            checkInvariant(
                errorMessage = "Failed to generate key $category after $MAX_ATTEMPTS attempts."
            ) {
                keyClient.start().proxy.generateKeyPair(
                    tenantId,
                    alias,
                    category,
                    scheme,
                )
            }
        }
        return response.id
    }

    fun hasTlsKey(restClient: RestClient<KeysRestResource>): Boolean {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to list keys after $MAX_ATTEMPTS attempts."
            ) {
                client.start().proxy.listKeys(
                    tenantId = "p2p",
                    skip = 0,
                    take = 20,
                    orderBy = "NONE",
                    category = "TLS",
                    schemeCodeName = null,
                    alias = P2P_TLS_KEY_ALIAS,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    ids = null,
                ).isNotEmpty()
            }
        }
    }

    fun generateTlsKey(restClient: RestClient<KeysRestResource>): String {
        return generateKeyPair(
            keysRestClient = restClient,
            tenantId = "p2p",
            alias = P2P_TLS_KEY_ALIAS,
            category = "TLS",
            scheme = ECDSA_SECP256R1_CODE_NAME
        )
    }
}
