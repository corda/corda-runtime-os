package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.security.PublicKey

/**
 * Crypto service which can sign as well as create new key pairs.
 */
interface CryptoService {
    /**
     * Returns true if the createWrappingKey operation is required otherwise false.
     * The wrapping key may not be required in situations such as HSM supports the wrapped keys natively or
     * wrapping key is global.
     */
    fun requiresWrappingKey(): Boolean

    /** Schemes which [CryptoService] supports.  */
    fun supportedSchemes(): Array<SignatureScheme>

    /** Schemes which [CryptoService] supports when using a wrapping key.  */
    fun supportedWrappingSchemes(): Array<SignatureScheme>

    /** Check if this [CryptoService] has a private key entry for the input alias.  */
    fun containsKey(alias: String): Boolean

    /**
     * Returns the [PublicKey] of the input alias or null if it doesn't exist.
     *
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun findPublicKey(alias: String): PublicKey?

    /**
     * Generates a new key to be used as a wrapping key. Some of the implementations may not have the notion of the wrapping key
     * in such cases the implementation should do nothing.
     * The method would be called only during the bootstrapping process.
     *
     * @param masterKeyAlias the alias of the key to be used as a wrapping key.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under the provided alias or return normally without overriding the key.
     *
     * @throws [CryptoServiceBadRequestException] if a key already exists under this alias (and [failIfExists] is set to true].
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean)

    /**
     * Generate and store an asymmetric key pair.
     * Note that schemeNumberID is Corda specific. Cross-check with the network operator for supported schemeNumberID
     * and their corresponding signature schemes. The main reason for using schemeNumberID and not algorithm OIDs is
     * because some schemes might not be standardised and thus an official OID might for this scheme not exist yet.
     *
     * Returns the [PublicKey] of the generated key pair.
     *
     * @throws [CryptoServiceBadRequestException] if the [signatureScheme] is not supported.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun generateKeyPair(alias: String, signatureScheme: SignatureScheme): PublicKey

    /**
     * Generates an asymmetric key pair, returning the public key and the private key material wrapped using the specified wrapping key.
     *
     * @param masterKeyAlias the alias of the key to be used as a wrapping key.
     * @param wrappedSignatureScheme the scheme of the key pair to be generated.
     *
     * @throws [CryptoServiceBadRequestException] if the wrapping key does not exists under the alias or the [wrappedSignatureScheme] is not supported.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun generateWrappedKeyPair(masterKeyAlias: String, wrappedSignatureScheme: SignatureScheme): WrappedKeyPair

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the default signature spec.
     * If specified the signAlgorithm determines the signature scheme used for signing.
     *
     * @throws [CryptoServiceBadRequestException] if the private key does not exists under the [alias], the key scheme is not supported,  or the [data] is empty array..
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun sign(alias: String, signatureScheme: SignatureScheme, data: ByteArray): ByteArray

    /**
     * Sign a byte array using the private key identified by the input alias.
     * Returns the signature bytes formatted according to the overriding signature spec.
     * If specified the signAlgorithm determines the signature scheme used for signing.
     *
     * @throws [CryptoServiceBadRequestException] if the private key does not exists under the [alias], the key scheme is not supported,  or the [data] is empty array..
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun sign(alias: String, signatureScheme: SignatureScheme, signatureSpec: SignatureSpec, data: ByteArray): ByteArray

    /**
     * Unwraps the provided wrapped key, using the specified wrapping key and signs the provided payload.
     *
     * @param wrappedKey the private key to be used for signing in a wrapped form.
     * @param data data to be signed.
     *
     * @throws [CryptoServiceBadRequestException] if the wrapping key does not exists under the master alias in [wrappedKey] or the [data] is empty array.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun sign(wrappedKey: WrappedPrivateKey, signatureSpec: SignatureSpec, data: ByteArray): ByteArray
}
