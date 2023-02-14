package net.corda.crypto.client

import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.v5.crypto.CompositeKey
import java.nio.ByteBuffer
import java.security.PublicKey

/**
 * Additional operations to [CryptoOpsClient] when you have raw data - like ByteBuffer instead of PublicKey.
 * Don't use it unless you exactly know what you are doing, the use case is to proxy the Flow OPS to the
 * [CryptoOpsClient] service implementation.
 */
// If we end up removing `filterMyKeysProxy` then we can drop `CryptoOpsProxyClient`
interface CryptoOpsProxyClient : CryptoOpsClient {
    /**
     * Filters the input [PublicKey]s down to a collection of keys that this tenant owns (has private keys for).
     *
     * @param tenantId The tenant owning the key.
     * @param candidateKeys The [ByteBuffer]s containing encoded [PublicKey]s to filter.
     *
     * @return A collection of [CryptoSigningKeys] containing encoded [PublicKey]s that this node owns.
     */
    // This path is not being currently used - consider removing it
    fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys

    /**
     * Looks up for keys by ids owned by tenant of [tenantId] (has private keys for).
     */
    fun lookupKeysByShortIdsProxy(tenantId: String, candidateKeys: ShortHashes): CryptoSigningKeys

    /**
     * Looks up for keys by ids (full ids) owned by tenant of [tenantId] (has private keys for).
     */
    fun lookupKeysByIdsProxy(tenantId: String, keyIds: SecureHashes): CryptoSigningKeys

    /**
     * Using the provided signing public key internally looks up the matching private key information and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * Default signature scheme for the key scheme is used.
     */
    fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        signatureSpec: CryptoSignatureSpec,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey
}