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
 * digests, security providers, key factories, and [SecureRandom] instance as well as some utility methods.
 */
interface CipherSchemeMetadata : KeyEncodingService, AlgorithmParameterSpecEncodingService {
    companion object {
        /**
         * List of digest algorithms that must not be used nor implemented due their vulnerabilities.
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
     * The map of initialized security providers used by the cipher suite where the key is the provider name.
     */
    val providers: Map<String, Provider>

    /**
     * The list of all available key schemes for the cipher suite.
     */
    val schemes: List<KeyScheme>

    /**
     * The list of all available digest algorithms for the cipher suite.
     */
    val digests: List<DigestScheme>

    /**
     * An instance of [SecureRandom] which should be used to generate cryptographically secure random value.
     */
    val secureRandom: SecureRandom

    /**
     * Finds the corresponding [KeyScheme] based on its [AlgorithmIdentifier]
     *
     * @throws IllegalArgumentException if the scheme is not supported
     */
    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme

    /**
     * Finds the corresponding [KeyScheme] based on the type of the [PublicKey].
     *
     * @throws IllegalArgumentException if the key type is not supported.
     */
    fun findKeyScheme(key: PublicKey): KeyScheme

    /**
     * Finds the corresponding [KeyScheme] based on the code name.
     *
     * @throws IllegalArgumentException if the scheme is not supported.
     */
    fun findKeyScheme(codeName: String): KeyScheme

    /**
     * Finds the corresponding [KeyFactory] based on the [KeyScheme].
     *
     * @throws IllegalArgumentException if the scheme is not supported.
     */
    fun findKeyFactory(scheme: KeyScheme): KeyFactory

    /**
     * Returns a default signature spec compatible with the specified [PublicKey].
     *
     * @return [SignatureSpec] with the signatureName formatted like "SHA256withECDSA" if that can be inferred or
     * otherwise null.
     */
    fun defaultSignatureSpec(publicKey: PublicKey): SignatureSpec?

    /**
     * Infers the signature spec from the [PublicKey] and [DigestAlgorithmName]. The [digest] may be ignored for some
     * public key types as the digest is integral part of the signing/verification, e.g. if the [publicKey] is 'EdDSA'
     * then the [digest] is set to "EdDSA" by the platform's default implementation.
     *
     * @return [SignatureSpec] with the signatureName formatted like "SHA256withECDSA" if that can be inferred or
     * otherwise null.
     */
    fun inferSignatureSpec(publicKey: PublicKey, digest: DigestAlgorithmName): SignatureSpec?

    /**
     * Returns list of the non-custom signature specs for the given [KeyScheme] with the signatureName
     * formatted like "SHA256withECDSA".
     */
    fun supportedSignatureSpec(scheme: KeyScheme): List<SignatureSpec>

    /**
     * Returns list of the non-custom signature specs for the given [KeyScheme] and [DigestAlgorithmName]
     * with the signatureName formatted like "SHA256withECDSA".
     */
    fun supportedSignatureSpec(scheme: KeyScheme, digest: DigestAlgorithmName): List<SignatureSpec>

    /**
     * Returns list of the [DigestAlgorithmName] for the given [KeyScheme] from which [SignatureSpec] can be inferred.
     */
    fun inferableDigestNames(scheme: KeyScheme): List<DigestAlgorithmName>
}