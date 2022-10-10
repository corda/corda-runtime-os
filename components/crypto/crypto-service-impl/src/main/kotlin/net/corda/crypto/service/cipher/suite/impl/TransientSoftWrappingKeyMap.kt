package net.corda.crypto.service.cipher.suite.impl

import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.service.PlatformCipherSuiteMetadata
import net.corda.crypto.service.cipher.suite.GeneratedWrappingKey
import net.corda.crypto.service.cipher.suite.WRAPPING_KEY_ENCODING_VERSION
import net.corda.crypto.service.cipher.suite.WrappingKeyMap
import net.corda.crypto.service.persistence.WrappingKeyInfo
import net.corda.crypto.service.persistence.WrappingKeyStore
import java.util.UUID

class TransientSoftWrappingKeyMap(
    private val metadata: PlatformCipherSuiteMetadata,
    private val store: WrappingKeyStore,
    private val master: WrappingKey
) : WrappingKeyMap {
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

    override fun getWrappingKey(): GeneratedWrappingKey {
        // for now - generate the key each time
        val wrappingKey = WrappingKey.generateWrappingKey(metadata)
        val wrappingKeyInfo = WrappingKeyInfo(
            encodingVersion = WRAPPING_KEY_ENCODING_VERSION,
            alias = UUID.randomUUID().toString(),
            algorithmName = wrappingKey.algorithm,
            keyMaterial = master.wrap(wrappingKey)
        )
        store.saveWrappingKey(wrappingKeyInfo)
        return GeneratedWrappingKey(
            alias = wrappingKeyInfo.alias,
            key = wrappingKey
        )
    }
}