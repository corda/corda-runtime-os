package net.corda.crypto.service.impl.softhsm

import net.corda.crypto.service.softhsm.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.service.softhsm.PrivateKeyMaterial
import net.corda.crypto.service.softhsm.SoftPrivateKeyWrapping
import net.corda.crypto.service.softhsm.SoftWrappingKeyMap
import net.corda.v5.cipher.suite.KeyMaterialSpec
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