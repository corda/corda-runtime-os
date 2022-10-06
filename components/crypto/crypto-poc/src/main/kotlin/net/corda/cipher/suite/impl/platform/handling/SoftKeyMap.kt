package net.corda.cipher.suite.impl.platform.handling

import net.corda.v5.cipher.suite.providers.signing.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

interface SoftKeyMap {
    fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey
    fun wrapPrivateKey(keyPair: KeyPair): PrivateKeyMaterial
}