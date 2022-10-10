package net.corda.crypto.service.cipher.suite

import net.corda.v5.cipher.suite.handlers.signing.KeyMaterialSpec
import java.security.PrivateKey

interface PrivateKeyWrapping {
    fun unwrap(spec: KeyMaterialSpec) : PrivateKey
    fun wrap(privateKey: PrivateKey) : PrivateKeyMaterial
}