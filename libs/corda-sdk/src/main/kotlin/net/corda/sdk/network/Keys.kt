@file:Suppress("DEPRECATION")

package net.corda.sdk.network

import net.corda.membership.rest.v1.HsmRestResource
import net.corda.membership.rest.v1.KeysRestResource
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Keys {

    companion object {
        const val P2P_TLS_KEY_ALIAS = "p2p-tls-key"
        const val P2P_TLS_CERTIFICATE_ALIAS = "p2p-tls-cert"
    }

    @Suppress("LongParameterList")
    fun assignSoftHsmAndGenerateKey(
        hsmRestClient: RestClient<HsmRestResource>,
        keysRestClient: RestClient<KeysRestResource>,
        holdingIdentityShortHash: String,
        category: String,
        scheme: String = ECDSA_SECP256R1_CODE_NAME,
        wait: Duration = 10.seconds
    ): String {
        hsmRestClient.use { hsmClient ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Assign Soft HSM operation for $category"
            ) {
                hsmClient.start().proxy.assignSoftHsm(holdingIdentityShortHash, category)
            }
        }

        return generateKeyPair(
            keysRestClient = keysRestClient,
            tenantId = holdingIdentityShortHash,
            alias = "$holdingIdentityShortHash-$category",
            category = category,
            scheme = scheme,
            wait = wait
        )
    }

    @Suppress("LongParameterList")
    fun generateKeyPair(
        keysRestClient: RestClient<KeysRestResource>,
        tenantId: String,
        alias: String,
        category: String,
        scheme: String = ECDSA_SECP256R1_CODE_NAME,
        wait: Duration = 10.seconds
    ): String {
        val response = keysRestClient.use { keyClient ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Generate key $category"
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

    fun hasTlsKey(restClient: RestClient<KeysRestResource>, wait: Duration = 10.seconds): Boolean {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "List keys"
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

    fun generateTlsKey(restClient: RestClient<KeysRestResource>, wait: Duration = 10.seconds): String {
        return generateKeyPair(
            keysRestClient = restClient,
            tenantId = "p2p",
            alias = P2P_TLS_KEY_ALIAS,
            category = "TLS",
            scheme = ECDSA_SECP256R1_CODE_NAME,
            wait = wait
        )
    }
}
