package net.corda.crypto.softhsm.impl

import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.softhsm.PrivateKeyMaterial
import net.corda.crypto.softhsm.SoftKeyMap
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

class TransientSoftKeyMap(
    private val wrapping: SoftPrivateKeyWrapping
) : SoftKeyMap {
    override fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey =
        wrapping.unwrap(spec)

    override fun wrapPrivateKey(keyPair: KeyPair, masterKeyAlias: String?): PrivateKeyMaterial =
        wrapping.wrap(keyPair.private, masterKeyAlias)
}