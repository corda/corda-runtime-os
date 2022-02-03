package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.DigestScheme
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.KeyFactory
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*

/**
 * Service which provides metadata about cipher suite, such as available signature schemes,
 * digests and security providers.
 */
interface CipherSchemeMetadata : KeyEncodingService, AlgorithmParameterSpecEncodingService {
    companion object {
        @JvmField
        val BANNED_DIGESTS: Set<String> = Collections.unmodifiableSet(setOf(
            "MD5",
            "MD2",
            "SHA-1",
            "MD4",
            "HARAKA-256",
            "HARAKA-512"
        ))
    }

    /**
     * The map of initialized security providers where the key is the provider name.
     */
    val providers: Map<String, Provider>

    /**
     * The list of all available key schemes for the cipher suite.
     */
    val schemes: Array<SignatureScheme>

    /**
     * The list of all available digest algorithms for the cipher suite with the provider name which implements it.
     */
    val digests: Array<DigestScheme>

    /** Get an instance of [SecureRandom] */
    val secureRandom: SecureRandom

    /**
     * Find the corresponding [SignatureScheme] based on its [AlgorithmIdentifier]
     *
     * @throws [IllegalArgumentException] if the scheme is not supported
     */
    fun findSignatureScheme(algorithm: AlgorithmIdentifier): SignatureScheme

    /**
     * Find the corresponding [SignatureScheme] based on the type of the input [PublicKey].
     *
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findSignatureScheme(key: PublicKey): SignatureScheme

    /**
     * Find the corresponding [SignatureScheme] based on the code name.
     *
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findSignatureScheme(codeName: String): SignatureScheme

    /**
     * Find the corresponding [KeyFactory] based on the [SignatureScheme].
     *
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findKeyFactory(scheme: SignatureScheme): KeyFactory
}