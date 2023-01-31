package net.corda.crypto.service

import net.corda.crypto.cipher.suite.KeyMaterialSpec
import java.security.PrivateKey

interface SoftPrivateKeyWrapping {
    fun unwrap(spec: KeyMaterialSpec) : PrivateKey
    fun wrap(privateKey: PrivateKey, masterKeyAlias: String?) : PrivateKeyMaterial
}