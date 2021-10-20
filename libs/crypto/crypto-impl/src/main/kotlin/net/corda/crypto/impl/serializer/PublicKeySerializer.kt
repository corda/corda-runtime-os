package net.corda.crypto.impl.serializer

import net.corda.crypto.impl.CipherSchemeMetadataProviderImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.serialization.SerializationCustomSerializer
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
class PublicKeySerializer : SerializationCustomSerializer<PublicKey, ByteArray> {

    private val cipherSchemeMetadata: CipherSchemeMetadata by lazy { CipherSchemeMetadataProviderImpl().getInstance() }

    override fun toProxy(obj: PublicKey): ByteArray = obj.encoded
    override fun fromProxy(proxy: ByteArray): PublicKey = cipherSchemeMetadata.decodePublicKey(proxy)
}