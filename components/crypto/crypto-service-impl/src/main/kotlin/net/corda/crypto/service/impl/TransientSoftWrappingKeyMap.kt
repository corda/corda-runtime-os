package net.corda.crypto.service.impl

import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.crypto.softhsm.WRAPPING_KEY_ENCODING_VERSION

class TransientSoftWrappingKeyMap(
    private val store: WrappingKeyStore,
    private val master: WrappingKey
) : SoftWrappingKeyMap {
    override fun getWrappingKey(alias: String): WrappingKey {
        val wrappingKeyInfo = store.findWrappingKey(alias)
            ?: throw IllegalStateException("The $alias is not created yet.")
        require(wrappingKeyInfo.encodingVersion == WRAPPING_KEY_ENCODING_VERSION) {
            "Unknown wrapping key encoding. Expected to be $WRAPPING_KEY_ENCODING_VERSION"
        }
        require(master.algorithm == wrappingKeyInfo.algorithmName) {
            "Expected algorithm is ${master.algorithm} but was ${wrappingKeyInfo.algorithmName}"
        }
        return master.unwrapWrappingKey(wrappingKeyInfo.keyMaterial)
    }

    override fun putWrappingKey(alias: String, wrappingKey: WrappingKey) {
        val wrappingKeyInfo = WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            algorithmName =  wrappingKey.algorithm,
            keyMaterial = master.wrap(wrappingKey)
        )
        store.saveWrappingKey(alias, wrappingKeyInfo)
    }

    override fun exists(alias: String): Boolean = store.findWrappingKey(alias) != null
}