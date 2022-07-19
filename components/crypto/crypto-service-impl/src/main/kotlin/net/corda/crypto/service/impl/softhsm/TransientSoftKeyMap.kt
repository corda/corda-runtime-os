package net.corda.crypto.service.impl.softhsm

import net.corda.crypto.service.softhsm.PrivateKeyMaterial
import net.corda.crypto.service.softhsm.SoftKeyMap
import net.corda.crypto.service.softhsm.SoftPrivateKeyWrapping
import net.corda.v5.cipher.suite.KeyMaterialSpec
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