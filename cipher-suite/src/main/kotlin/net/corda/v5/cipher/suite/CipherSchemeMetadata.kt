package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.DigestScheme
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.KeyFactory
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Collections

/**
 * Service which provides metadata about cipher suite, such as available key schemes,
 * digests, security providers, key factories, and [SecureRandom] instance.
 */
interface CipherSchemeMetadata : KeyEncodingService, AlgorithmParameterSpecEncodingService {
    companion object {
        /**
         * List of digest algorithms that must not be used due their vulnerabilities.
         */
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
    val schemes: List<KeyScheme>

    /**
     * The list of all available digest algorithms for the cipher suite with the provider name which implements it.
     */
    val digests: List<DigestScheme>

    /** Get an instance of [SecureRandom] */
    val secureRandom: SecureRandom

    /**
     * Find the corresponding [KeyScheme] based on its [AlgorithmIdentifier]
     *
     * @throws [IllegalArgumentException] if the scheme is not supported
     */
    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme

    /**
     * Find the corresponding [KeyScheme] based on the type of the input [PublicKey].
     *
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findKeyScheme(key: PublicKey): KeyScheme

    /**
     * Find the corresponding [KeyScheme] based on the code name.
     *
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findKeyScheme(codeName: String): KeyScheme

    /**
     * Find the corresponding [KeyFactory] based on the [KeyScheme].
     *
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    fun findKeyFactory(scheme: KeyScheme): KeyFactory

    /**
     * Infers the signature spec from the [PublicKey] and [DigestAlgorithmName]. If the [publicKey] is 'EdDSA'
     * then the [digest] is ignored and signatureName is set to "EdDSA".
     *
     * @return [SignatureSpec] with the signatureName formatted like "SHA256withECDSA" if that can be inferred or
     * otherwise null.
     */
    fun inferSignatureSpec(publicKey: PublicKey, digest: DigestAlgorithmName): SignatureSpec?

    /**
     * Returns list of the non-custom signature specs for the given [KeyScheme] with the signatureName
     * formatted like "SHA256withECDSA" .
     */
    fun supportedSignatureSpec(scheme: KeyScheme): List<SignatureSpec>

    /**
     * Returns list of the [DigestAlgorithmName] for the given [KeyScheme] from which [SignatureSpec] can be inferred.
     */
    fun inferableDigestNames(scheme: KeyScheme): List<DigestAlgorithmName>
}