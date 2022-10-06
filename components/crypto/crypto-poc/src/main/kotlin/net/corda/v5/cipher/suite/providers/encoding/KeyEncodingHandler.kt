package net.corda.v5.cipher.suite.providers.encoding

import net.corda.v5.cipher.suite.scheme.KeyScheme
import java.security.PublicKey

interface KeyEncodingHandler {
    val rank: Int
    fun decodePublicKey(encodedKey: ByteArray): PublicKey?
    fun decodePublicKey(encodedKey: String): PublicKey?
    fun encodeAsByteArray(publicKey: PublicKey): ByteArray = publicKey.encoded
    fun encodeAsString(scheme: KeyScheme, publicKey: PublicKey): String
}