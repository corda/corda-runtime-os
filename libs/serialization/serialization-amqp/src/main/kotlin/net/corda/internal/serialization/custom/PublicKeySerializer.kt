package net.corda.internal.serialization.custom

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.serialization.SerializationCustomSerializer
import java.security.PublicKey

/**
 * A serializer that writes out a public key in X.509 format.
 */
class PublicKeySerializer(private val cipherSchemeMetadata : CipherSchemeMetadata)
    : SerializationCustomSerializer<PublicKey, ByteArray> {
    override fun toProxy(obj: PublicKey): ByteArray = obj.encoded
    override fun fromProxy(proxy: ByteArray): PublicKey = cipherSchemeMetadata.decodePublicKey(proxy)
}