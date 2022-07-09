package net.corda.crypto.ecdh.impl

import net.corda.crypto.ecdh.ECDH_KEY_AGREEMENT_ALGORITHM
import java.security.KeyPair
import java.security.Provider
import java.security.PublicKey
import javax.crypto.KeyAgreement

class EphemeralKeyPair(
    private val provider: Provider,
    private val keyPair: KeyPair,
    otherPublicKey: PublicKey,
    digestName: String
) : AbstractECDHKeyPair(keyPair.public, otherPublicKey, digestName) {
    override fun deriveSharedSecret(): ByteArray =
        KeyAgreement.getInstance(ECDH_KEY_AGREEMENT_ALGORITHM, provider).apply {
            init(keyPair.private)
            doPhase(otherPublicKey, true)
        }.generateSecret()
}