package net.corda.crypto.client

import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.CompositeKey
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID

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
     * @return A collection of [CryptoPublicKeys] containing encoded [PublicKey]s that this node owns.
     */
    fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoPublicKeys

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage.
     *
     * @param tenantId The tenant owning the key.
     * @param context the optional key/value operation context.
     *
     * @return The [CryptoPublicKey] containing encoded [PublicKey] of the generated [KeyPair].
     */
    fun freshKeyProxy(tenantId: String, context: KeyValuePairList): CryptoPublicKey

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Associates the public key to
     * an external id.
     *
     * @param tenantId The tenant owning the key.
     * @param externalId Some id associated with the key, the service doesn't use any semantic beyond association.
     * @param context the optional key/value operation context.
     *
     * @return The [CryptoPublicKey] containing encoded [PublicKey] of the generated [KeyPair].
     */
    fun freshKeyProxy(
        tenantId: String,
        externalId: UUID,
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
}