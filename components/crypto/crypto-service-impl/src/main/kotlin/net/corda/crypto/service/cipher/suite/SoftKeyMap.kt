package net.corda.crypto.service.cipher.suite

import net.corda.v5.cipher.suite.handlers.signing.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

interface SoftKeyMap {
    fun unwrapPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey
    fun wrapPrivateKey(keyPair: KeyPair): PrivateKeyMaterial
}