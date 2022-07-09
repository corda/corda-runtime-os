package net.corda.crypto.ecdh.impl

import net.corda.crypto.client.CryptoOpsClient
import java.security.PublicKey

class StableKeyPair internal constructor(
    private val cryptoOpsClient: CryptoOpsClient,
    private val tenantId: String,
    publicKey: PublicKey,
    otherPublicKey: PublicKey,
    digestName: String
) : AbstractECDHKeyPair(publicKey, otherPublicKey, digestName) {
    override fun deriveSharedSecret(): ByteArray =
        cryptoOpsClient.deriveSharedSecret(tenantId, publicKey, otherPublicKey)
}