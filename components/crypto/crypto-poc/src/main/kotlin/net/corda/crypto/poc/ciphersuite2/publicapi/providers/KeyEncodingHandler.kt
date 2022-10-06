package net.corda.crypto.poc.ciphersuite2.publicapi.providers

import java.security.PublicKey

interface KeyEncodingHandler {
    fun decodePublicKey(encodedKey: ByteArray): PublicKey
    fun decodePublicKey(encodedKey: String): PublicKey
    fun encodeAsByteArray(publicKey: PublicKey): ByteArray = publicKey.encoded
    fun encodeAsString(publicKey: PublicKey): String
}