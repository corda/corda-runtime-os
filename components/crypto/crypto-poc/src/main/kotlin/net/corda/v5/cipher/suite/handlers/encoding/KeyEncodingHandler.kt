package net.corda.v5.cipher.suite.handlers.encoding

import net.corda.v5.cipher.suite.KeyScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.PublicKey

interface KeyEncodingHandler {
    val rank: Int
    fun getAlgorithmIdentifier(publicKey: PublicKey): AlgorithmIdentifier?
    fun decode(encodedKey: ByteArray): PublicKey?
    fun decode(scheme: KeyScheme, publicKeyInfo: SubjectPublicKeyInfo, encodedKey: ByteArray): PublicKey?
    fun decodePem(encodedKey: String): PublicKey?
    fun decodePem(scheme: KeyScheme, publicKeyInfo: SubjectPublicKeyInfo, pemContent: ByteArray): PublicKey?
    fun encodeAsByteArray(publicKey: PublicKey): ByteArray = publicKey.encoded
    fun encodeAsPem(scheme: KeyScheme, publicKey: PublicKey): String
}