@file:Suppress("DEPRECATION")
// Needed for the v1.KeysRestResource import statement

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

    /**
     * Combination method, first assign a soft HSM, then generate a key-pair
     * @param hsmRestClient of type RestClient<HsmRestResource>
     * @param keysRestClient of type RestClient<KeysRestResource>
     * @param holdingIdentityShortHash the holding identity of the node
     * @param category the category of the HSM
     * @param scheme the key's scheme describing which type of the key pair to generate, has default value
     * @param wait Duration before timing out, default 10 seconds
     * @return the ID of the newly generated key pair
     */
    @Suppress("LongParameterList", "DEPRECATION")
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

    /**
     * Generate a key-pair
     * @param keysRestClient of type RestClient<KeysRestResource>
     * @param tenantId the holding identity of the node
     * @param alias the alias under which the new key pair will be stored
     * @param category the category of the HSM
     * @param scheme the key's scheme describing which type of the key pair to generate, has default value
     * @param wait Duration before timing out, default 10 seconds
     * @return the ID of the newly generated key pair
     */
    @Suppress("LongParameterList", "DEPRECATION")
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

    /**
     * Lists the P2P TLS keys to determine if one exists
     * @param restClient of type RestClient<KeysRestResource>
     * @param alias the alias under which the P@P TLS key pair is stored, has default value
     * @param wait Duration before timing out, default 10 seconds
     * @return true if the key exists, otherwise false
     */
    @Suppress("DEPRECATION")
    fun hasTlsKey(
        restClient: RestClient<KeysRestResource>,
        alias: String = P2P_TLS_KEY_ALIAS,
        wait: Duration = 10.seconds
    ): Boolean {
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
                    alias = alias,
                    masterKeyAlias = null,
                    createdAfter = null,
                    createdBefore = null,
                    ids = null,
                ).isNotEmpty()
            }
        }
    }

    /**
     * Helper to specifically generate a P2P TLS key
     * @param restClient of type RestClient<KeysRestResource>
     * @param alias the alias under which the P@P TLS key pair is stored, has default value
     * @param wait Duration before timing out, default 10 seconds
     * @return the ID of the newly generated key pair
     */
    @Suppress("DEPRECATION")
    fun generateTlsKey(
        restClient: RestClient<KeysRestResource>,
        alias: String = P2P_TLS_KEY_ALIAS,
        wait: Duration = 10.seconds
    ): String {
        return generateKeyPair(
            keysRestClient = restClient,
            tenantId = "p2p",
            alias = alias,
            category = "TLS",
            scheme = ECDSA_SECP256R1_CODE_NAME,
            wait = wait
        )
    }
}
