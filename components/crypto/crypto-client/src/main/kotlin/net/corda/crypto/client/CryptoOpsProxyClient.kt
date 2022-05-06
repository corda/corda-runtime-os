package net.corda.crypto.client

import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey

/**
 * Additional operations to [CryptoOpsClient] when you have raw data - like ByteBuffer instead of PublicKey.
 * Don't use it unless you exactly know what you are doing, the use case is to proxy the Flow OPS to the
 * [CryptoOpsClient] service implementation.
 */
interface CryptoOpsProxyClient : CryptoOpsClient {
    /**
     * Filters the input [PublicKey]s down to a collection of keys that this tenant owns (has private keys for).
     *
     * @param tenantId The tenant owning the key.
     * @param candidateKeys The [ByteBuffer]s containing encoded [PublicKey]s to filter.
     *
     * @return A collection of [CryptoSigningKeys] containing encoded [PublicKey]s that this node owns.
     */
    fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     *
     * @return The [CryptoPublicKey] containing encoded [PublicKey] of the generated [KeyPair].
     */
    fun freshKeyProxy(tenantId: String, scheme: String, context: KeyValuePairList): CryptoPublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to
     * an external id.
     *
     * @param tenantId The tenant owning the key.
     * @param externalId Some id associated with the key, the service doesn't use any semantic beyond association.
     * @param scheme the key's scheme code name describing which type of the key to generate.
     * @param context the optional key/value operation context.
     *
     * @return The [CryptoPublicKey] containing encoded [PublicKey] of the generated [KeyPair].
     */
    fun freshKeyProxy(
        tenantId: String,
        externalId: String,
        scheme: String,
        context: KeyValuePairList
    ): CryptoPublicKey

    /**
     * Using the provided signing public key internally looks up the matching private key information and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * Default signature scheme for the key scheme is used.
     */
    fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey

    /**
     * Generates a new key to be used as a wrapping key. Some implementations may not have the notion of
     * the wrapping key in such cases the implementation should do nothing (note that [requiresWrappingKey] should
     * return false for such implementations).
     *
     * @param configId the HSM's configuration id which the key is generated in.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     *
     * @throws [CryptoServiceBadRequestException] if a key already exists under this alias
     * and [failIfExists] is set to true.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun createWrappingKey(
        configId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    )
}