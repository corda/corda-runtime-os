package net.corda.crypto.softhsm.impl

import net.corda.crypto.softhsm.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.softhsm.PrivateKeyMaterial
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.v5.cipher.suite.KeyMaterialSpec
import java.security.PrivateKey

// Currently this is not strictly needed; we could instead call unwrap and wrap directly on the spec.keyMaterial
// However in the future we would like to support HSMs where these operations would involve calls through to the HSMs.
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