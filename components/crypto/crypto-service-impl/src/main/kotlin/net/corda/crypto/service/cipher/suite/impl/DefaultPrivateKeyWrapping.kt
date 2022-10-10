package net.corda.crypto.service.cipher.suite.impl

import net.corda.crypto.service.cipher.suite.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.service.cipher.suite.PrivateKeyMaterial
import net.corda.crypto.service.cipher.suite.PrivateKeyWrapping
import net.corda.crypto.service.cipher.suite.WrappingKeyMap
import net.corda.v5.cipher.suite.handlers.signing.KeyMaterialSpec
import java.security.PrivateKey

class DefaultPrivateKeyWrapping(
    private val wrappingKeyMap: WrappingKeyMap
) : PrivateKeyWrapping {
    override fun unwrap(spec: KeyMaterialSpec): PrivateKey {
        require(!spec.masterKeyAlias.isNullOrBlank()) {
            "The masterKeyAlias is not specified"
        }
        val wrappingKey = wrappingKeyMap.getWrappingKey(spec.masterKeyAlias!!)
        return wrappingKey.unwrap(spec.keyMaterial)
    }

    override fun wrap(privateKey: PrivateKey): PrivateKeyMaterial {
        val wrappingKey = wrappingKeyMap.getWrappingKey()
        return PrivateKeyMaterial(
            encodingVersion = PRIVATE_KEY_ENCODING_VERSION,
            keyMaterial = wrappingKey.key.wrap(privateKey),
            masterKeyAlias = wrappingKey.alias
        )
    }
}