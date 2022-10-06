package net.corda.v5.cipher.suite.providers.encoding

import java.security.PublicKey

interface KeyEncodingHandler {
    fun decodePublicKey(encodedKey: ByteArray): PublicKey
    fun decodePublicKey(encodedKey: String): PublicKey
    fun encodeAsByteArray(publicKey: PublicKey): ByteArray = publicKey.encoded
    fun encodeAsString(publicKey: PublicKey): String
}