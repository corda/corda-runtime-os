package net.corda.crypto

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import java.security.PublicKey

/**
 * The SigningService is responsible for storing and using private keys to sign things. An implementation of this may, for example,
 * call out to a hardware security module that enforces various auditing and frequency-of-use requirements.
 */
interface SigningService {
    /** Schemes which are supported. */
    val supportedSchemes: Array<SignatureScheme>

    /**
     * Returns the public key for the given alias.
     */
    fun findPublicKey(alias: String): PublicKey?

    /**
     * Generates a new random key pair using the configured default key scheme and adds it to the internal key storage.
     * Returns the public part of the pair.
     * The alias has to be the lower case as some file base key store implementations are case insensitive but
     * majority of HSMs are.
     */
    fun generateKeyPair(alias: String): PublicKey

    /**
     * Using the provided signing public key internally looks up the matching private key and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * Default signature scheme for the key scheme is used.
     */
    fun sign(publicKey: PublicKey, data: ByteArray): DigitalSignature.WithKey

    /**
     * Using the provided signing public key internally looks up the matching private key and signs the data.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     */
    fun sign(publicKey: PublicKey, signatureSpec: SignatureSpec, data: ByteArray): DigitalSignature.WithKey

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme (signAlgorithm).
     * Default signature scheme for the key scheme is used.
     */
    fun sign(alias: String, data: ByteArray): ByteArray

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the signature scheme (signAlgorithm).
     */
    fun sign(alias: String, signatureSpec: SignatureSpec, data: ByteArray): ByteArray
}
