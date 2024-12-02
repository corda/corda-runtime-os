package net.corda.sdk.network

import net.corda.crypto.core.CryptoConsts.Categories.KeyCategory
import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.KeyPairIdentifier
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Keys(val restClient: CordaRestClient) {

    companion object {
        const val P2P_TLS_KEY_ALIAS = "p2p-tls-key"
        const val P2P_TLS_CERTIFICATE_ALIAS = "p2p-tls-cert"
    }

    /**
     * Combination method, first assign a soft HSM, then generate a key-pair
     * Checks if the key exists before generating
     * @param holdingIdentityShortHash the holding identity of the node
     * @param category the category of the HSM
     * @param scheme the key's scheme describing which type of the key pair to generate, has default value
     * @param wait Duration before timing out, default 10 seconds
     * @return the KeyPairIdentifier of the newly generated key pair
     */
    fun assignSoftHsmAndGenerateKey(
        holdingIdentityShortHash: ShortHash,
        category: KeyCategory,
        scheme: String = ECDSA_SECP256R1_CODE_NAME,
        wait: Duration = 10.seconds
    ): KeyPairIdentifier {
        restClient.hsmClient.postHsmSoftTenantidCategory(holdingIdentityShortHash.value, category.value)

        val keyAlias = "$holdingIdentityShortHash-$category"

        val keyAlreadyExists = executeWithRetry(
            waitDuration = wait,
            operationName = "List keys"
        ) {
            restClient.keyManagementClient.getKeyTenantid(
                tenantid = holdingIdentityShortHash.value,
                category = category.value,
                schemecodename = scheme,
                alias = keyAlias
            ).toList().firstOrNull()
        }

        if (keyAlreadyExists != null) {
            return KeyPairIdentifier(keyAlreadyExists.second.keyId)
        }

        return generateKeyPair(
            tenantId = holdingIdentityShortHash.value,
            alias = keyAlias,
            category = category,
            scheme = scheme
        )
    }

    /**
     * Generate a key-pair
     * @param tenantId the holding identity of the node
     * @param alias the alias under which the new key pair will be stored
     * @param category the category of the HSM
     * @param scheme the key's scheme describing which type of the key pair to generate, has default value
     * @return the KeyPairIdentifier of the newly generated key pair
     */
    fun generateKeyPair(
        tenantId: String,
        alias: String,
        category: KeyCategory,
        scheme: String = ECDSA_SECP256R1_CODE_NAME,
    ): KeyPairIdentifier {
        return restClient.keyManagementClient.postKeyTenantidAliasAliasCategoryHsmcategorySchemeScheme(
            tenantId,
            alias,
            category.value,
            scheme
        )
    }

    /**
     * Lists the P2P TLS keys to determine if one exists
     * @param alias the alias under which the P@P TLS key pair is stored, has default value
     * @param wait Duration before timing out, default 10 seconds
     * @return true if the key exists, otherwise false
     */
    fun hasTlsKey(
        alias: String = P2P_TLS_KEY_ALIAS,
        wait: Duration = 10.seconds
    ): Boolean {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "List keys"
        ) {
            restClient.keyManagementClient.getKeyTenantid(
                tenantid = "p2p",
                category = KeyCategory.TLS_KEY.value,
                alias = alias
            ).isNotEmpty()
        }
    }

    /**
     * Helper to specifically generate a P2P TLS key
     * @param alias the alias under which the P@P TLS key pair is stored, has default value
     * @return the KeyPairIdentifier of the newly generated key pair
     */
    fun generateTlsKey(
        alias: String = P2P_TLS_KEY_ALIAS,
    ): KeyPairIdentifier {
        return generateKeyPair(
            tenantId = "p2p",
            alias = alias,
            category = KeyCategory.TLS_KEY,
            scheme = ECDSA_SECP256R1_CODE_NAME
        )
    }
}
