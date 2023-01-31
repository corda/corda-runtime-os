package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.service.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.service.PrivateKeyMaterial
import net.corda.crypto.service.SoftPrivateKeyWrapping
import net.corda.crypto.service.SoftWrappingKeyMap
import java.security.PrivateKey

class DefaultSoftPrivateKeyWrapping(
    private val wrappingKeyMap: SoftWrappingKeyMap
) : SoftPrivateKeyWrapping {
    override fun unwrap(spec: KeyMaterialSpec): PrivateKey {
        require(!spec.masterKeyAlias.isNullOrBlank()) {
            "The masterKeyAlias is not specified"
        }
        val wrappingKey = wrappingKeyMap.getWrappingKey(spec.masterKeyAlias!!)
        return wrappingKey.unwrap(spec.keyMaterial)
    }

    override fun wrap(privateKey: PrivateKey, masterKeyAlias: String?): PrivateKeyMaterial {
        require(!masterKeyAlias.isNullOrBlank()) {
            "The masterKeyAlias is not specified"
        }
        val wrappingKey = wrappingKeyMap.getWrappingKey(masterKeyAlias)
        return PrivateKeyMaterial(
            encodingVersion = PRIVATE_KEY_ENCODING_VERSION,
            keyMaterial = wrappingKey.wrap(privateKey)
        )
    }
}