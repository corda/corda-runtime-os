package net.corda.crypto.softhsm

import net.corda.crypto.cipher.suite.KeyMaterialSpec
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

interface SoftKeyMap {
    fun getPrivateKey(publicKey: PublicKey, spec: KeyMaterialSpec): PrivateKey
    fun wrapPrivateKey(keyPair: KeyPair, masterKeyAlias: String?): PrivateKeyMaterial
}