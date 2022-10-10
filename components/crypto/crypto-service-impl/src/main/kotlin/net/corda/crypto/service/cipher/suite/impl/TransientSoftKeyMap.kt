package net.corda.crypto.service.cipher.suite.impl

import net.corda.crypto.service.cipher.suite.PrivateKeyMaterial
import net.corda.crypto.service.cipher.suite.SoftKeyMap
import net.corda.crypto.service.cipher.suite.PrivateKeyWrapping
import net.corda.v5.cipher.suite.handlers.signing.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

class TransientSoftKeyMap(
    private val wrapping: PrivateKeyWrapping
) : SoftKeyMap {
    override fun unwrapPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey =
        wrapping.unwrap(spec)

    override fun wrapPrivateKey(keyPair: KeyPair): PrivateKeyMaterial =
        wrapping.wrap(keyPair.private)
}