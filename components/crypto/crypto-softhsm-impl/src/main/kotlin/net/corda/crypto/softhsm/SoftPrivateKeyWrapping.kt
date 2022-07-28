package net.corda.crypto.softhsm

import net.corda.v5.cipher.suite.KeyMaterialSpec
import java.security.PrivateKey

interface SoftPrivateKeyWrapping {
    fun unwrap(spec: KeyMaterialSpec) : PrivateKey
    fun wrap(privateKey: PrivateKey, masterKeyAlias: String?) : PrivateKeyMaterial
}